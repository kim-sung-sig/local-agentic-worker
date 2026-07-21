# [Analysis] Nuxt/Temporal TS Migration — Stage 2: Drizzle Schema & Migration Baseline

**Plan:** `docs/superpowers/plans/2026-07-20-nuxt-control-plane-temporal-migration.md`
**PDCA phase:** Check

| Stage 2 acceptance criterion | Evidence | Result |
|---|---|---|
| `control_plane`/`engine` two-schema Drizzle definition, schema-first | `packages/db/src/control-plane/**`, `packages/db/src/engine/**`; `npm run test -w @agentic-worker/db` (schema tests) | Met |
| Migration SQL only via `drizzle-kit generate` (never hand-edited), `push` never used | `packages/db/drizzle.control-plane.config.ts`, `drizzle.engine.config.ts`; `db:generate*` scripts; no `db:push*` script anywhere in `packages/db/package.json` | Met |
| Cross-schema FK forbidden (hard boundary) | `test/schema.test.ts` asserts every FK's target schema equals its source schema; `test/migration-apply.test.ts` regexes generated SQL for `REFERENCES "engine"`/`REFERENCES "control_plane"` across the boundary | Met |
| Empty-PostgreSQL migration apply verification (Docker-gated) | `test/migration-apply.test.ts` — Docker was UP, both schemas + all 13 mapped tables created cleanly | Met (PASS) |
| Dev DB backup/restore verification (Docker-gated, reachable-only) | `test/dev-db-backup-restore.test.ts` — `postgres-source` (postgres:17.6, localhost:15432) was reachable this session; pg_dump→restore ran and PASSED | Met (PASS) |
| Analysis doc in `docs/03-analysis/` (Stage 1 style) | this file | Met |
| Root `npm run test`/`lint`/`typecheck` green, Stage 1 no regression | see Verification evidence below | Met |

## What this task did

### Legacy (Java Flyway, `public`) ↔ target (Drizzle) mapping

| Legacy table | Target | Notes |
|---|---|---|
| `project` (V1,V6) | `control_plane.projects` | `repository_uri` partial unique index (only when not null), `base_branch` default `'main'`, `credential_ref`/`local_path` nullable |
| `issue` (V1) | `control_plane.issues` | FK→`projects` (same schema), `unique(project_id, issue_number)`, `status` default `'OPEN'`, idx(project_id) |
| `project_notification` (V7) | `control_plane.notifications` | `notification_id`/`event_key` unique (idempotency), cursor idx(project_id,id), unread idx(project_id,read_at,id); `workflow_run_id` kept as a **non-FK** uuid column (cross-schema reference) |
| `engine_workflow_run` (V5) | `engine.workflow_runs` | `temporal_workflow_id` unique, `status` default `'RUNNING'`; `ticket_id` is a **non-FK logical identifier**, never joined to `control_plane.issues` |
| `engine_stage_gate` (V5) | `engine.stage_gates` | FK→`workflow_runs` (same schema), idx(run_id) |
| `engine_attempt_record` (V5) | `engine.attempt_records` | FK→`workflow_runs` (same schema), `unique(run_id, attempt_number)`, `qa_score` int |
| `agent_job` (V2–V4) | **none — intentionally dropped** | see below |
| (none — new) | `control_plane.documents`, `document_revisions`, `document_artifacts`, `users`, `memberships`, `sessions`, `outbox_events` | new in the target model; not present in legacy |

**`agent_job` intentional drop rationale:** `agent_job` was the legacy execution-state table for a single Claude
agent run (status/started_at/finished_at/phase/document_path/claude_session_id/pr_url). The target model
replaces that responsibility with the `engine` schema's durable Workflow projection —
`workflow_runs` (one row per Temporal workflow execution) plus `attempt_records` (one row per QA-loop
attempt within a run). This is a genuine model change, not a 1:1 rename: the plan's fixed model
(`## 고정 모델과 계약`) defines `engine.*` as Workflow-projection-only, and `agent_job`'s per-job execution
bookkeeping is superseded by that projection rather than mapped column-for-column. It is not ported to
Drizzle at all — carrying it forward would duplicate state Temporal already owns durably.

### Two-schema boundary: rule and enforcement

The plan's hard constraint is that `engine.*` (`workflow_runs`, `stage_gates`, `attempt_records`) must never
FK-reference, join, or query `control_plane.*` (`projects`, `issues`, `documents`, ...), and vice versa. Any
cross-schema relationship is expressed through non-FK logical identifier columns
(`engine.workflow_runs.ticket_id`, `control_plane.notifications.workflow_run_id`) — plain `uuid()` columns
with no `.references()`.

This is enforced two ways, both exercised by the test suite (not just documented):

1. **Source-level** (`packages/db/test/schema.test.ts`): iterates every table's `getTableConfig(table).foreignKeys`
   and asserts each FK's target table's schema equals the source table's own schema. A cross-schema `.references()`
   accidentally added to a Drizzle table definition fails this test before any SQL is even generated.
2. **Generated-SQL level** (`packages/db/test/migration-apply.test.ts`, runs with or without Docker): regexes the
   generated migration SQL for `REFERENCES "engine"` inside the control-plane migration and
   `REFERENCES "control_plane"` inside the engine migration — both assert zero matches. This catches boundary
   violations that might only manifest after `drizzle-kit generate`, independent of the TS source check.

### Package placement: `packages/db` shared workspace, why

Adopted the plan's recommendation (A) as-is: a single shared npm workspace `@agentic-worker/db`, physically
split internally by schema ownership (`src/control-plane/`, `src/engine/`, separate `drizzle.control-plane.config.ts`
/ `drizzle.engine.config.ts`, separate `drizzle/control-plane/` / `drizzle/engine/` generate output). The
alternative — colocating schema definitions inside each consuming app (`apps/control-plane`,
`apps/temporal-worker`) — was rejected because:

- The root ESLint flat config scopes lint to `packages/*` and `apps/temporal-worker`, and deliberately
  `ignore`s `apps/control-plane` (see Stage 1 analysis doc). Colocating `control_plane` schema TS inside the
  Nuxt app would put it outside the enforced lint/typecheck gate.
- Both apps need DB access to different schemas (control-plane owns `control_plane`, temporal-worker owns
  `engine`); a shared package with schema-separated exports lets drizzle-kit tooling/versioning live in one
  place while keeping schema *ownership* (who has write access) a logical contract, not a physical file location.
- Physical subdirectory + separate config + separate migration output folder makes an accidental
  cross-schema FK harder to introduce and easy to catch in review, on top of the automated tests above.

`src/index.ts` exposes the two schemas as separate namespaces (`export * as controlPlane`, `export * as engine`)
rather than one flattened re-export, so the schema boundary stays visible at the import call site for Stage 3
consumers (`import { controlPlane } from '@agentic-worker/db'` vs `import { engine } from '@agentic-worker/db'`).

### 2-config vs 1-config outcome

The plan flagged this as undecided, contingent on drizzle-kit's named-schema support. In practice no
workaround was needed: each of the two configs points its `schema` option at a different subpath
(`src/control-plane/index.ts` / `src/engine/index.ts`), so `drizzle-kit generate` only ever sees the tables
reachable from that subpath and emits only that schema's `CREATE SCHEMA` + tables. The 2-config split was not
forced by a drizzle-kit limitation (no `schemaFilter` workaround was required) — it fell out naturally from
Task 1's physical `src/control-plane` / `src/engine` split. The two configs are a clean expression of the same
ownership boundary, not a tooling compromise.

### Empty-Postgres migration verification result

Docker was available. `packages/db/test/migration-apply.test.ts` spun up a disposable `postgres:16-alpine`
container, applied both generated `.sql` files directly (not via `drizzle-kit migrate`'s journal bootstrap —
see Task 2 report concern), and asserted via `information_schema` that both schemas and all 13 mapped tables
(10 in `control_plane`, 3 in `engine`) were created. **Result: PASS.** Container was torn down in `afterAll`;
verified no orphan via `docker ps -a --filter ancestor=postgres:16-alpine` (empty).

### Dev DB backup/restore verification result

Earlier in this session the dev DB port (localhost:15432) was reported CLOSED, which per the plan's
"확인 필요" note would have made this step a documented SKIP. Mid-session the orchestrator confirmed Docker
had come up and the `postgres-source` container (postgres:17.6, `agentic_worker` DB, `dev_user`/`dev_password`)
was reachable on 15432 — so this step became **required**, not skippable, and was executed for real rather
than left as a skip stub.

Because the source server is v17.6, a v16 pg_dump/psql client can fail with a server-version mismatch, so the
dump/restore both run through a `postgres:17-alpine` container (client only, no local install) reaching the
host DB via `host.docker.internal`. New test: `packages/db/test/dev-db-backup-restore.test.ts`. Sequence:

1. Reachability probe from the host process via the `pg` driver (cheap, no docker) — only proceeds if it
   succeeds; otherwise skips with a logged, reproducible reason (this path exists in the code but did not
   trigger this run, since the source was in fact reachable).
2. Logical backup: `docker run --rm -e PGPASSWORD=*** postgres:17-alpine pg_dump -h host.docker.internal -p 15432 -U dev_user --no-owner --no-privileges agentic_worker` — read-only against the source, captured as a string (no writes to `postgres-source`).
3. Fresh empty `postgres:17-alpine` container as the restore target.
4. Restore via `docker exec -i <target> psql -v ON_ERROR_STOP=1 -U restore_test -d restore_test` fed the dump
   through stdin — `psql`, not the node `pg` driver, because `psql` natively understands the
   `COPY ... FROM stdin` blocks pg_dump's plain-SQL output emits; the driver's simple query protocol does not.
5. Assert `information_schema.tables` (schema `public`) equals the expected 8-table Java Flyway set:
   `agent_job`, `engine_attempt_record`, `engine_stage_gate`, `engine_workflow_run`,
   `flyway_schema_history`, `issue`, `project`, `project_notification`.
6. Teardown restore container in `afterAll`.

**Result: PASS.** Dump size 13,575 bytes; all 8 expected tables present after restore. Verified no orphan via
`docker ps -a --filter ancestor=postgres:17-alpine` (empty after the run).

This verifies the existing Java dev data can be backed up safely before Nuxt/Drizzle takes schema ownership
of the same database (plan "전환·삭제 기준" item 1) — the dump/restore path itself works; it does **not**
migrate or transform that data into the new `control_plane`/`engine` shape (see Remaining scope).

## Verification evidence

```text
# packages/db
npm run test -w @agentic-worker/db        # 3 test files, 21 tests, all passed
  test/schema.test.ts             14 passed  (Task 1, unchanged)
  test/migration-apply.test.ts     5 passed  (Task 2: 3 Docker-gated + 2 SQL-boundary, ran & passed — Docker was up)
  test/dev-db-backup-restore.test.ts 2 passed (Task 3: ran & passed — source DB was reachable)

npx vitest run test/dev-db-backup-restore.test.ts --reporter=verbose
  [dev-db-backup-restore] dump size: 13575 bytes
  [dev-db-backup-restore] restored tables: agent_job, engine_attempt_record, engine_stage_gate,
    engine_workflow_run, flyway_schema_history, issue, project, project_notification
  ✓ produces a non-trivial dump
  ✓ restores every expected public table from the Java Flyway schema

docker ps -a --filter ancestor=postgres:17-alpine   # empty — no orphan containers
docker ps -a --filter ancestor=postgres:16-alpine   # empty — no orphan containers

# root
npm run test        # workspaces: temporal-worker 1/1, contracts 5/5, db 21/21 — all pass
npm run lint         # eslint . — 0 errors
npm run typecheck    # control-plane (nuxt typecheck), temporal-worker, contracts, db — all clean
```

## Remaining scope (tracked in the migration plan, not this task)

- Actual data import/transformation from the legacy `public` tables into `control_plane`/`engine` rows is
  Stage 8 (cutover), out of scope here — this stage only proves the schema/migration tooling and that the
  existing dev data can be safely backed up, not that it has been carried over.
- `drizzle-kit migrate`'s journal-based apply path (`__drizzle_migrations` bootstrap) was not itself exercised
  by the empty-DB verification test (Task 2 used the raw generated SQL directly); the `db:migrate:*` npm
  scripts exist but are unverified end-to-end. Worth a smoke check once an app actually needs to run them
  (Stage 3+).
- No app (`apps/control-plane`, `apps/temporal-worker`) references `@agentic-worker/db` yet — API/runtime
  wiring is explicitly out of scope for Stage 2 and is carried into Stage 3.
- `notifications.id` stays `bigserial` (legacy-shaped) while every other PK is `uuid`; how the app layer
  represents that bigint (string vs bigint) is a Stage 3 concern (carried from Task 1's report).
