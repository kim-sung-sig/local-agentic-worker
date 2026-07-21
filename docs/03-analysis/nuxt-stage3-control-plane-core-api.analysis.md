# [Analysis] Nuxt/Temporal TS Migration — Stage 3: Control Plane Core API

**Plan:** `docs/superpowers/plans/2026-07-21-nuxt-control-plane-core-api.md`
**PDCA phase:** Check

| Stage 3 acceptance criterion | Evidence | Result |
|---|---|---|
| Project CRUD (register/list/get), `credentialRef` never in responses | `server/api/projects/index.{get,post}.ts`, `[projectId]/index.get.ts`, `utils/project-service.ts`; `test/project-service.test.ts`; e2e step 1 asserts `!('credentialRef' in project)` | Met |
| Issue lifecycle (create with `issueNumber` sequencing, list, status patch) | `server/api/projects/[projectId]/issues/*`, `server/api/issues/[issueId]/*`, `utils/issue-service.ts`; `test/issue-service.test.ts`; e2e steps 3-5 | Met |
| Document + revision authoring, immutable revisions, approval as a status flip (never a new revision) | `server/api/issues/[issueId]/documents/index.post.ts`, `server/api/documents/[documentId]/revisions/index.post.ts`, `server/api/document-revisions/[revisionId]/approve.post.ts`, `utils/document-service.ts`; `test/document-service.test.ts`; e2e steps 6-8 | Met |
| Outbox side effects on domain events (`ISSUE_CREATED`, `DOCUMENT_REVISION_APPROVED`) written transactionally | `utils/outbox.ts` (`withOutbox`), used by `issue-service.ts` and `document-service.ts`; `test/outbox.test.ts` | Met |
| Notifications list/unread-count/SSE stream (replay via `Last-Event-ID`) | `server/api/projects/[projectId]/notifications/*`; `test/notification-service.test.ts`, `test/notification-stream.test.ts`; e2e steps 9-10 | Met |
| End-to-end golden path against real, reachable Postgres | `test/api.e2e.test.ts` — see Verification evidence below | Met (PASS) |
| `db:migrate:control-plane` closed as a carry-item | See "db:migrate:control-plane — honest outcome" below | **Not met — carried forward** |
| Analysis doc in `docs/03-analysis/` (Stage 2 style) | this file | Met |
| Root `npm run test`/`lint`/`typecheck` green, no regression | see Verification evidence below | Met |

## What this task did

### The golden-path e2e test

`apps/control-plane/test/api.e2e.test.ts` boots the real Nuxt/Nitro app via `@nuxt/test-utils/e2e`'s
`setup()` (same pattern as Task 6's `notification-stream.test.ts`) and drives the full Stage 3 surface
through real HTTP calls against the running server:

1. `POST /api/projects` → asserts the returned project has no `credentialRef` key at all (not just
   `null`/`undefined` — `expect(project).not.toHaveProperty('credentialRef')`).
2. `GET /api/projects` → contains the created project.
3. `POST /api/projects/:id/issues` → `issueNumber === 1`, `status === 'OPEN'`.
4. `GET /api/projects/:id/issues` → length 1 (scoped to *this* fresh project, not a global count —
   see "Test isolation" below).
5. `PATCH /api/issues/:id/status` → `IN_PROGRESS`.
6. `POST /api/issues/:id/documents` → `latestRevision.revisionNumber === 1`, `approvedAt === null`.
7. `POST /api/documents/:id/revisions` → `revisionNumber === 2`.
8. `insertTestUser()` (below) then `POST /api/document-revisions/:id/approve` → `approvedAt` not null.
9. `GET /api/projects/:id/notifications` → array.
10. `GET /api/projects/:id/notifications/unread-count` → `typeof count === 'number'`.

This exercises every route added in Tasks 2-5 in one continuous domain narrative (register → file →
plan → approve → notify), which is exactly what the individual per-service Vitest suites (`project-service`,
`issue-service`, `document-service`, `outbox`, `notification-service`) cannot prove on their own: that the
HTTP layer, Zod validation, and service layer compose correctly end to end through the real Nitro request
pipeline, not just at the function-call level.

### `insertTestUser()` — why direct DB insert, not a fabricated API route

There is no user-registration HTTP route in this codebase yet — auth/user management is Stage 4 scope.
The plan explicitly forbids inventing one just to make this test convenient. `insertTestUser()` instead
calls `getDb()` from `../server/utils/db.js` directly and inserts a `control_plane.users` row, returning
its `id` as the approver id for the `POST /api/document-revisions/:id/approve` call. This reuses the
exact same `getDb()` singleton the app itself connects through — `@nuxt/test-utils`'s `setup()` boots the
app in-process against the same `DATABASE_URL`, so there is no second pool or connection-string drift to
account for (identical to how `notification-stream.test.ts` seeds notifications directly via `getDb()`
in Task 6).

### Test isolation / rerunnability against a persistent (non-disposable) DB

Unlike the per-service unit-test suites and Stage 2's migration tests, this e2e test does **not** spin up
a disposable testcontainer — it runs against the actual dev Postgres (`postgres-source`, localhost:15432),
which persists rows across every run. Two schema-level `unique` constraints made naive reruns fail
immediately if not handled:

- `projects_repository_uri_unique` (partial unique index on `repository_uri` when non-null)
- `users_email_unique`

The test avoids both by randomizing `repositoryUri` and the project `name` (`Math.random()` suffix) and
the test-user's `email` (`Math.random()` suffix) on every run, so each invocation creates a brand-new
project/user pair rather than colliding with a previous run's rows. Every assertion that could be read as
a "global count" (e.g. `issues` length) is instead scoped to the fresh `project.id` this run created —
per Task 6's issue list route, a fresh project's issue list is naturally isolated regardless of how many
prior e2e runs left rows behind, so the length-1 assertion holds on every rerun. Verified by running the
suite twice in a row (see Verification evidence) with no manual cleanup between runs — both passed.

The golden path also produces two outbox rows (`ISSUE_CREATED` from step 3, `DOCUMENT_REVISION_APPROVED`
from step 8) as a side effect of running through `withOutbox`. This is expected and accepted per the
plan — no publisher process runs in this test, so the rows are simply written and never consumed; they do
not affect any assertion.

### `db:migrate:control-plane` — honest outcome (carry-item, not closed)

The plan's Step 1 framed this task as the first real exercise of `db:migrate:control-plane`, expecting it
to close the carry-item flagged in the Stage 2 analysis doc ("the `db:migrate:*` npm scripts exist but are
unverified end-to-end"). **That did not happen, and this doc records the honest reason rather than
papering over it:**

**`db:migrate:control-plane` is broken as currently configured.** `packages/db/drizzle.control-plane.config.ts`
defines only `dialect` and `schema`/`out` — it has no `dbCredentials` block, and `drizzle-kit migrate` (unlike
`generate`) needs a live connection string to apply migrations and bootstrap its `__drizzle_migrations`
journal table. There is no CLI flag on `drizzle-kit migrate` to supply a URL override in place of
`dbCredentials` in the config file, so the script cannot run as-is against `localhost:15432` or any other
target without first adding that block (and deciding how it reads `DATABASE_URL`, since the config file is
plain TS, not environment-aware today).

Because this task's charter is scoped to `apps/control-plane/test/` and `docs/03-analysis/` only — no file
under `packages/db` may be touched — fixing the config was out of bounds here. Instead, the `control_plane`
schema was confirmed already present on `localhost:15432` (verified with
`to_regclass('control_plane.users')`, `.projects`, `.issues`, `.documents`, `.document_revisions`,
`.notifications`, `.outbox_events` — all resolved to real regclasses, none `null`) — applied in Task 6 by
executing the generated `drizzle/control-plane/*.sql` files directly via a `pg.Pool`, the same non-journal
approach Stage 2's `migration-apply.test.ts` used for its disposable-container verification. This task's
e2e test therefore runs against a schema that is correct and present, but was never applied via
`drizzle-kit migrate` itself.

**This remains an open, unresolved carry-item** — not "verified working" as the plan hoped. Follow-up:
add a `dbCredentials` (or equivalent env-driven) block to `drizzle.control-plane.config.ts` (and its
`engine` counterpart) in a future task, then smoke-test `db:migrate:control-plane` against a disposable
DB to actually close this out.

### Test-suite determinism: `fileParallelism: false` masks, does not fix, missing wait-strategies

`apps/control-plane/vitest.config.ts` sets `fileParallelism: false` (added in an earlier task) to avoid
Windows/Docker contention across the module's test files: five suites that each boot their own
`postgres:16-alpine` testcontainer (`project-service`, `issue-service`, `document-service`,
`notification-service`, `outbox`) plus this task's e2e suite and Task 6's `notification-stream` suite,
both of which boot a full Nitro dev server against the shared dev Postgres. Running these in parallel
forks on this Windows/Docker setup previously produced intermittent "the database system is starting up"
failures.

This setting is a scheduling workaround, not a root fix. The shared `test/support/postgres.ts` helper
(introduced partway through this stage) already has the correct fix — a `Wait.forLogMessage` readiness
strategy on the testcontainer so tests block until Postgres is actually accepting connections, not just
until the container process has started. Tasks 1-3's suites (`project-service.test.ts`,
`issue-service.test.ts`, `document-service.test.ts`) predate that helper and were never retrofitted to use
it; `fileParallelism: false` currently papers over their missing wait-strategy by removing the concurrency
that would otherwise expose the race. **Tracked follow-up:** retrofit Tasks 1-3's bootstraps onto
`test/support/postgres.ts` so the suite's correctness does not depend on sequential scheduling.

## Verification evidence

```text
# apps/control-plane, run twice in a row against the persistent dev DB (no manual cleanup between runs)
npx vitest run test/api.e2e.test.ts
  Test Files  1 passed (1)
       Tests  1 passed (1)

npx vitest run test/api.e2e.test.ts   # second run, same DB, no reset
  Test Files  1 passed (1)
       Tests  1 passed (1)

# confirmed hitting the real dev Postgres, not a testcontainer:
#   DATABASE_URL was unset in the shell -> getDb() default resolves to
#   postgresql://dev_user:dev_password@localhost:15432/agentic_worker
#   docker ps: postgres-source  0.0.0.0:15432->5432/tcp   (the only listener on 15432)

# schema presence check before writing the test (raw pg.Pool, no packages/db file touched):
node -e "... to_regclass('control_plane.users' | '.projects' | '.issues' | '.documents' |
  '.document_revisions' | '.notifications' | '.outbox_events') ..."
  -> all resolved (non-null), schema was already applied from Task 6

# root
npm run test         # all workspaces incl. apps/control-plane's 7 test files green, no regression
npm run lint          # eslint . — 0 errors
npm run typecheck     # control-plane (nuxt typecheck), temporal-worker, contracts, db — all clean
```

## Remaining scope (tracked, not this task)

- **Auth/authz** (Stage 4): every route in this stage is unauthenticated; `approvedByUserId` is accepted
  as a bare client-supplied id with no session/identity check. `insertTestUser()`'s direct-DB-insert
  approach is itself a stand-in for the registration flow Stage 4 will add.
- **Live SSE push via LISTEN/NOTIFY + outbox consumer** (Stage 6): the notification stream route replays
  history correctly (Task 6) but nothing currently drains `outbox_events` or pushes new rows to open SSE
  connections in real time; this task's e2e run leaves two more unconsumed outbox rows behind
  (`ISSUE_CREATED`, `DOCUMENT_REVISION_APPROVED`), same as every prior run — expected until a consumer
  exists.
- **Screen wiring** (Stage 7): no frontend consumes any of this stage's routes yet.
- **`runtimeConfig.databaseUrl` is dead config**: `nuxt.config.ts` declares `runtimeConfig.databaseUrl`,
  but `server/utils/db.ts`'s `getDb()` reads `process.env.DATABASE_URL` directly (by design, so the same
  module works from plain Vitest service tests outside the Nitro runtime — see its own doc comment). No
  route or service currently calls `useRuntimeConfig()` for this value, so the Nuxt-level config key is
  presently unused. Worth reconciling when Stage 4 wires real request-scoped config/session handling.
- **`db:migrate:control-plane` / `db:migrate:engine`** — broken as configured (see above), unresolved
  carry-item into a future task, not closed by this one.
