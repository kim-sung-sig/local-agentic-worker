# [Analysis] Nuxt/Temporal TS Migration — Stage 4: Control Plane Authentication & Authorization

**Plan:** `docs/superpowers/plans/2026-07-21-nuxt-control-plane-auth.md`
**PDCA phase:** Check

| Stage 4 acceptance criterion | Evidence | Result |
|---|---|---|
| Email/password auth: `POST /api/auth/register`, `POST /api/auth/login` issue an HttpOnly session cookie | `server/api/auth/register.post.ts`, `server/api/auth/login.post.ts`, `server/utils/auth-service.ts` (Task 2, prior to this task) | Met |
| Argon2 password hashing, raw session token never persisted (only its SHA-256 hash) | `auth-service.ts` (Task 2) | Met (verified by reading, not re-derived in this task) |
| `requireSession`/`requireProjectRole` guard every Stage 3 route; `MEMBER` default, `MAINTAINER` for approval | `server/utils/auth-guard.ts` (Task 3), applied across `server/api/**` (Task 4) — this task did not add guards, it consumed already-guarded routes | Met (pre-existing, exercised by this task's e2e) |
| `registerProject` creates an `OWNER` membership for the registering user, in the same transaction as the project insert | `server/utils/project-service.ts` `registerProject(input, ownerUserId)`; `test/project-service.test.ts` — see TDD evidence below | Met |
| e2e: unauthenticated request → 401 | `test/auth.e2e.test.ts` test 1 | Met |
| e2e: cross-project request → 403 | `test/auth.e2e.test.ts` test 2 | Met |
| e2e: role-gated approval — MEMBER rejected 403, MAINTAINER succeeds | `test/auth.e2e.test.ts` test 3 | Met |
| Analysis doc in `docs/03-analysis/` (Stage 3 style) | this file | Met |
| Root `npm run test`/`lint`/`typecheck` green, no regression | see Verification evidence below | Met |

## What this task did

### Part A — `registerProject` auto-creates an OWNER membership (TDD)

Prior to this task, `registerProject` inserted only a `control_plane.projects` row; nothing recorded
who owned the project, even though Task 4's `requireProjectRole` guard already depended on
`control_plane.memberships` existing for every project-scoped route. This left a gap: a project
registered through the real API had no membership rows at all, so its own owner would immediately get
403'd by the guards Task 4 had just wired in — the only reason Stage 3/4's own tests didn't already
hit this was that `api.e2e.test.ts` and `notification-stream.test.ts` were manually inserting an
`OWNER` membership row after calling the register route, as a stand-in.

**TDD sequence actually followed:**

1. Added a failing assertion to `test/project-service.test.ts` ("creates an OWNER membership for the
   registering user") that calls `registerProject(input, ownerUserId)` and then queries
   `control_plane.memberships` directly for `(ownerUserId, project.id)`, expecting `role === 'OWNER'`.
   The suite's `beforeAll` DDL (previously only `CREATE TABLE control_plane.projects`) was extended to
   also create `control_plane.users` and `control_plane.memberships` with the same FK/unique shape as
   the real schema, and to seed one `users` row to satisfy the membership's FK.
2. Confirmed RED: `expected undefined to be 'OWNER'` (no second param existed on `registerProject`,
   and no membership row was ever inserted).
3. Implemented: `registerProject(input, ownerUserId: string)` now wraps the project insert and the
   membership insert in one `getDb().transaction(...)` — a project row is never created without an
   accompanying `OWNER` membership, and vice versa is impossible by construction (the membership insert
   references the just-inserted project's `id`).
4. Confirmed GREEN: `project-service.test.ts` — 4/4 passed.
5. Updated the one real caller, `server/api/projects/index.post.ts`, to pass
   `(await requireSession(event)).id` as `ownerUserId` — it already called `requireSession` for the
   401 guard (Task 4), so this reuses that result rather than resolving the session twice.

**Deviation, disclosed:** the plan's Step 1 scoped this task's file touches to `test/auth.e2e.test.ts`,
`project-service.ts`/`.test.ts`, and `api.e2e.test.ts` "only if de-dup needed". Once `registerProject`
started auto-inserting the `OWNER` membership, **two** existing e2e suites broke, not one:
`test/api.e2e.test.ts` (anticipated by the plan) *and* `test/notification-stream.test.ts` (not named in
the plan) — both had their own manual `memberships` insert for the registering user, and both now hit
`memberships_user_id_project_id_unique` violations. Fixing only the named file would have left the full
suite red, contradicting the plan's own root-gate requirement ("root gates must stay green after every
task"), so `notification-stream.test.ts`'s redundant seed line was also removed (and its now-unused
`userId` destructure). This is a strictly mechanical follow-on of Part A, not a new domain change.

### Part B — `test/auth.e2e.test.ts`

Three real, unstubbed assertions, following `api.e2e.test.ts`'s exact `@nuxt/test-utils/e2e` `setup()`
and cookie-threading pattern (a `registerAndLogin(email)` helper using plain `fetch` against the test
server so the `set-cookie` response header is readable, then threading the extracted `session_token`
cookie into subsequent `$fetch` calls):

1. **401** — `$fetch('/api/projects')` with no cookie rejects with `response.status === 401`.
2. **403 cross-project** — `owner` registers a project (becomes `OWNER` via Part A); a separately
   registered `outsider` (never given any membership) requesting `GET /api/projects/:id` rejects with
   `response.status === 403`.
3. **403/success role-gated approval** — `owner` registers a project, files an issue, drafts a
   document, and adds a second revision — all as the owner, via the real routes. A `member` and a
   `maintainer` are registered separately and seeded into `control_plane.memberships` by direct insert
   (no membership-invitation API exists yet — same direct-DB-insert precedent as
   `api.e2e.test.ts`'s `insertTestUser`). `member`'s `POST /api/document-revisions/:id/approve` rejects
   with 403; `maintainer`'s call succeeds with `approvedAt` not null.

**Honest RED/GREEN note:** test 1 and 2 (401/403) passed on the first run, not after an observed
failure. This is expected, not a shortcut — the guards they exercise (`requireSession`,
`requireProjectRole`) were already implemented and TDD'd in Tasks 3-4, which ran before this task in
the plan's sequence; this task's job was to *prove* that pre-existing behavior end-to-end, not to build
it. The one piece of genuinely new behavior in this task — `registerProject`'s OWNER-membership
insert — *was* driven RED-then-GREEN in `project-service.test.ts` (Part A above). Test 3 of
`auth.e2e.test.ts` also passed on first run for the same reason: it exercises the MAINTAINER-vs-MEMBER
rank comparison already implemented in `auth-guard.ts`'s `ROLE_RANK`, not new code from this task.

## Verification evidence

```text
# apps/control-plane
npx vitest run test/project-service.test.ts
  # before the fix: 1 failed (creates an OWNER membership for the registering user) - RED
  # after the fix:  4 passed - GREEN

npx vitest run test/auth.e2e.test.ts
  Test Files  1 passed (1)
       Tests  3 passed (3)

npx vitest run test/api.e2e.test.ts        # after removing the now-redundant manual OWNER seed
  Test Files  1 passed (1)
       Tests  1 passed (1)

npx vitest run test/notification-stream.test.ts   # after removing its now-redundant manual OWNER seed
  Test Files  1 passed (1)
       Tests  2 passed (2)

npx vitest run   # full apps/control-plane suite
  Test Files  10 passed (10)
       Tests  38 passed (38)
  # Note: two earlier attempts at this same full-suite run each showed exactly one testcontainer suite
  # (issue-service.test.ts, then outbox.test.ts, on different attempts) fail with
  # "the database system is starting up" - re-running each failing file in isolation passed
  # immediately. This is the pre-existing flaky testcontainer-startup race documented in the Stage 3
  # analysis doc ("fileParallelism: false masks, does not fix, missing wait-strategies" -
  # project-service/issue-service/document-service/outbox predate test/support/postgres.ts's readiness
  # strategy). Not introduced or fixed by this task; the third attempt of the full suite ran clean.

# root, from C:/git/agentic-worker
npm run test        # all 4 workspaces green: control-plane 38/38, temporal-worker 1/1, contracts 5/5, db 23/23
npm run lint         # eslint . - 0 errors
npm run typecheck    # control-plane (nuxt typecheck), temporal-worker, contracts, db - all clean

# Docker: postgres-source, 0.0.0.0:15432->5432/tcp, healthy, confirmed running throughout
```

## Remaining scope (tracked, not this task)

- **GitHub OAuth/OIDC** — explicitly out of scope per this plan's Global Constraints; first auth step
  is email/password only, as specified.
- **Membership-invitation API** — there is still no HTTP route to add a member/maintainer to a
  project. Every test that needs a non-owner role (`api.e2e.test.ts`'s approver,
  `auth.e2e.test.ts`'s `member`/`maintainer`) seeds `control_plane.memberships` by direct `getDb()`
  insert. This is an accepted stand-in per the plan, not a hidden gap, but it means the real product
  has no way yet for an `OWNER` to add teammates to a project outside of direct DB access.
  Building it is future scope.
- **Screen wiring (Stage 7)** — no frontend consumes the auth routes or session cookie yet; login/
  registration only exist as HTTP endpoints exercised by tests.
- **`db:migrate:control-plane` — still broken, still unresolved.** This is the same carry-item flagged
  in the Stage 3 analysis doc (`drizzle.control-plane.config.ts` has no `dbCredentials` block, so
  `drizzle-kit migrate` cannot run against `localhost:15432` or anywhere else without it). This task
  did not touch `packages/db`, per its file-scope constraint, so this remains open. The
  `control_plane.users.password_hash` column and the `memberships` table used by this task's tests were
  both already present on `localhost:15432` before this task started (applied in an earlier task via
  direct SQL, not via `drizzle-kit migrate`'s journal) — this task did not need to, and did not, apply
  any manual `ALTER`/DDL against the dev database itself; it only added DDL to a disposable
  testcontainer inside `project-service.test.ts`.
- **Fragile test mechanics carried over from Stage 3, not fixed here:** the testcontainer
  wait-strategy gap (see Verification evidence above) and `fileParallelism: false`'s scheduling
  workaround are unchanged; this task did not touch `vitest.config.ts` or `test/support/postgres.ts`.
- **Session/cookie hardening deferred to production config:** `secure: process.env.NODE_ENV ===
  'production'` (Task 2) means a session cookie set during local `npm run dev`/test runs is not marked
  `Secure` — this is the plan's documented, intentional exception for local HTTP dev, not a gap
  introduced or re-litigated by this task.
