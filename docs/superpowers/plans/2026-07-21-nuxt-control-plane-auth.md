# Nuxt Control Plane Authentication & Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Stage 4 of `docs/superpowers/plans/2026-07-20-nuxt-control-plane-temporal-migration.md` — email/password authentication, session cookies, project-role membership, and server-side authorization on every route added in Stage 3.

**Architecture:** A password/session auth layer (`server/utils/auth-service.ts`) issues an HttpOnly session cookie backed by a hashed token in `control_plane.sessions`. Two H3 utilities — `requireSession(event)` and `requireProjectRole(event, projectId, minRole)` — are called at the top of every existing route handler to enforce 401/403 before any domain logic runs. No new route in this plan changes Stage 3's response shapes; it only adds a rejection path in front of them.

**Tech Stack:** `@node-rs/argon2` (Argon2 password hashing, prebuilt binaries — avoids native-toolchain build issues with the plain `argon2` package on Windows), Node `crypto` (session tokens, hashed with SHA-256 before storage), H3 cookies (`getCookie`/`setCookie`), Drizzle, Vitest, `@nuxt/test-utils` (reusing Stage 3's patterns).

## Global Constraints

- Password hashing is Argon2 — copied verbatim from the migration plan's `## 트랜잭션·알림·인증 규칙`
  ("비밀번호는 Argon2 해시로 저장한다").
- First auth step is email/password with project roles; GitHub OAuth/OIDC is explicitly a later stage
  — copied verbatim from `## 명시적 기본값` ("인증 첫 단계: 이메일/비밀번호와 프로젝트 역할. GitHub
  OAuth/OIDC는 후속 단계"). Do not build any OAuth flow in this plan.
- Session cookie must be HttpOnly and SameSite — copied from the same section ("세션은 암호화
  HttpOnly/Secure/SameSite cookie를 사용"). **Documented exception:** `Secure` is set conditionally
  (`secure: process.env.NODE_ENV === 'production'`), because a `Secure` cookie is never sent by a
  browser back to the app over plain `http://localhost` in local dev — setting it unconditionally
  would make every local dev/test session silently fail to authenticate. This is a standard, necessary
  adaptation of the constraint, not a violation of it; do not "fix" this by disabling `Secure` in
  production too.
- The raw session token is never stored — only its SHA-256 hash is persisted in
  `control_plane.sessions.token`; the raw value exists only in the HttpOnly cookie sent to the client.
  A compromised database dump must not be directly usable as a valid session.
- Every route added in Stage 3 (`docs/superpowers/plans/2026-07-21-nuxt-control-plane-core-api.md`)
  must call `requireSession` and, where project-scoped, `requireProjectRole` before touching the
  database — no route may be left open. The one exception is the two new auth routes this plan adds
  (`POST /api/auth/register`, `POST /api/auth/login`), which must be reachable without a session.
- Role model (first pass, revisable): `'OWNER' | 'MAINTAINER' | 'MEMBER'`, ordered by that same
  precedence (`OWNER` satisfies any `minRole` check, `MEMBER` satisfies only `MEMBER`). Document
  approval (`POST /api/document-revisions/:id/approve`) requires at least `MAINTAINER`; every other
  Stage 3 route requires at least `MEMBER`. This is a deliberately minimal mapping — do not invent
  additional roles or permission granularity beyond what the acceptance tests below require.
- TDD throughout: a failing Vitest test before its implementation, for every task.
- One task = one commit. Do not mix domains in a single commit.
- Do not modify anything under `src/main/java`, `contracts/`, `control-plane-app/`,
  `agent-engine-app/`, `frontend/`, `apps/temporal-worker/`, or `packages/contracts/`.
- Root gates must stay green after every task: `npm run test`, `npm run lint`, `npm run typecheck`.

---

## File structure

```
packages/db/
  src/control-plane/users.ts              -- Modify: add passwordHash (Task 1)
  drizzle/control-plane/000X_*.sql        -- Modify: regenerate via drizzle-kit generate (Task 1)
  test/schema.test.ts                     -- Modify: extend users describe block (Task 1)

apps/control-plane/
  server/
    utils/
      auth-service.ts                     -- Task 2: register/authenticate/session issue+lookup
      auth-guard.ts                       -- Task 3: requireSession, requireProjectRole
      project-service.ts                  -- Modify (Task 5): registerProject creates an OWNER membership
      issue-service.ts                    -- unchanged (guards live in route handlers, not services)
      document-service.ts                 -- unchanged
      notification-service.ts             -- unchanged
    api/
      auth/
        register.post.ts                  -- Task 2
        login.post.ts                     -- Task 2
      projects/
        index.get.ts                      -- Modify (Task 4): requireSession
        index.post.ts                     -- Modify (Task 4): requireSession
        [projectId]/
          index.get.ts                    -- Modify (Task 4): requireSession + requireProjectRole(MEMBER)
          issues/
            index.get.ts                  -- Modify (Task 4)
            index.post.ts                 -- Modify (Task 4)
          notifications/
            index.get.ts                  -- Modify (Task 4)
            unread-count.get.ts            -- Modify (Task 4)
            read.post.ts                   -- Modify (Task 4)
            stream.get.ts                  -- Modify (Task 4): cookie-based auth (EventSource can't set headers)
      issues/
        [issueId]/
          index.get.ts                    -- Modify (Task 4): requireSession + resolve issue's project for the role check
          status.patch.ts                 -- Modify (Task 4)
          documents/
            index.post.ts                 -- Modify (Task 4)
      documents/
        [documentId]/
          revisions/
            index.post.ts                 -- Modify (Task 4)
      document-revisions/
        [revisionId]/
          approve.post.ts                 -- Modify (Task 4): requireProjectRole(MAINTAINER), not MEMBER
  test/
    auth-service.test.ts                  -- Task 2
    auth-guard.test.ts                    -- Task 3
    auth.e2e.test.ts                      -- Task 5: 401 unauthenticated, 403 wrong project, role-gated approval
```

---

### Task 1: Add `passwordHash` to `users` (packages/db)

**Files:**
- Modify: `packages/db/src/control-plane/users.ts`
- Modify: `packages/db/test/schema.test.ts`
- Regenerate: `packages/db/drizzle/control-plane/*`

**Interfaces:**
- Produces: `controlPlane.users` gains a `passwordHash: text('password_hash')` column (nullable — a
  user row can exist without a password yet if created by a future non-password flow; Task 2's
  `registerUser` always sets it, but the column itself must not be `notNull()`, since forcing that now
  would block any future auth method that doesn't start from a password).

- [ ] **Step 1: Write the failing schema test**

Modify `packages/db/test/schema.test.ts`, inside the existing
`describe('control_plane.users / memberships / sessions', ...)` block, add:

```ts
it('supports password_hash on users, nullable for future non-password auth methods', () => {
  const names = columnNames(controlPlane.users)
  expect(names).toContain('password_hash')

  const column = getTableConfig(controlPlane.users).columns.find((c) => c.name === 'password_hash')
  expect(column?.notNull).toBe(false)
})
```

- [ ] **Step 2: Run test, verify RED**

Run: `cd packages/db && npx vitest run test/schema.test.ts`
Expected: FAIL — `password_hash` not in column list.

- [ ] **Step 3: Add the column**

Modify `packages/db/src/control-plane/users.ts`:

```ts
import { text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'

/** A human account that can authenticate and hold project memberships. */
export const users = controlPlaneSchema.table('users', {
  id: uuid('id').primaryKey().defaultRandom(),
  email: text('email').notNull(),
  name: text('name'),
  passwordHash: text('password_hash'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('users_email_unique').on(table.email),
])
```

- [ ] **Step 4: Regenerate the migration**

Run: `cd packages/db && npm run db:generate:control-plane`
Expected: a new `000X_*.sql` migration adding `password_hash` — do not hand-edit it.

- [ ] **Step 5: Run test, verify GREEN, and root gates**

Run: `cd packages/db && npx vitest run` then from repo root `npm run test && npm run lint && npm run typecheck`.

```bash
git add packages/db/src/control-plane/users.ts packages/db/test/schema.test.ts packages/db/drizzle/control-plane
git commit -m "feat: add password_hash to users for email/password auth"
```

---

### Task 2: Auth service — register, authenticate, session issue/lookup

**Files:**
- Create: `apps/control-plane/server/utils/auth-service.ts`
- Create: `apps/control-plane/server/api/auth/register.post.ts`
- Create: `apps/control-plane/server/api/auth/login.post.ts`
- Create: `apps/control-plane/test/auth-service.test.ts`
- Modify: `apps/control-plane/package.json` (add `@node-rs/argon2`)

**Interfaces:**
- Consumes: `getDb()` (Stage 3, `server/utils/db.ts`).
- Produces (consumed by Task 3's guard and Task 4's routes):
  ```ts
  export interface RegisterUserInput { email: string, password: string, name?: string }
  export interface AuthenticatedUser { id: string, email: string, name: string | null }
  export interface IssuedSession { rawToken: string, expiresAt: Date }

  export async function registerUser(input: RegisterUserInput): Promise<AuthenticatedUser>
  export async function verifyPassword(email: string, password: string): Promise<AuthenticatedUser | null>
  export async function issueSession(userId: string): Promise<IssuedSession>
  export async function resolveSession(rawToken: string): Promise<AuthenticatedUser | null>
  ```
- `issueSession` generates `rawToken = crypto.randomBytes(32).toString('hex')`, stores
  `sha256(rawToken)` as `sessions.token`, and sets `expiresAt` to 7 days from now. `resolveSession`
  hashes the incoming raw token the same way, looks it up, and returns `null` (not throw) for
  missing/expired sessions — callers treat `null` as "not authenticated."
- Session expiry check is a plain `expiresAt > now()` comparison done in application code
  (`resolveSession`), not a DB-side scheduled cleanup — expired rows are simply never matched, and
  purging them is out of scope for this task.

- [ ] **Step 1: Add the Argon2 dependency**

Modify `apps/control-plane/package.json` to add `"@node-rs/argon2": "^2.0.2"` to `dependencies`. Run
`npm install` from the repo root afterward.

- [ ] **Step 2: Write the failing service test**

Create `apps/control-plane/test/auth-service.test.ts`, reusing `test/support/postgres.ts` from Stage
3's Task 4 (container bootstrap now needs `control_plane.users` with `password_hash` and
`control_plane.sessions`):

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { registerUser, verifyPassword, issueSession, resolveSession } from '../server/utils/auth-service.js'
// ... container bootstrap via test/support/postgres.ts, creating control_plane.users (with
// password_hash) and control_plane.sessions matching their Drizzle definitions.

describe('registerUser / verifyPassword', () => {
  it('registers a user with a hashed password, never the plaintext', async () => {
    const user = await registerUser({ email: 'dev@example.com', password: 'correct horse battery staple' })

    expect(user.email).toBe('dev@example.com')
    expect(user).not.toHaveProperty('passwordHash')

    const row = await db.execute(sql`select password_hash from control_plane.users where id = ${user.id}`)
    expect(row.rows[0].password_hash).not.toBe('correct horse battery staple')
  })

  it('authenticates with the correct password and rejects the wrong one', async () => {
    await registerUser({ email: 'auth-check@example.com', password: 'right-password' })

    expect(await verifyPassword('auth-check@example.com', 'right-password')).not.toBeNull()
    expect(await verifyPassword('auth-check@example.com', 'wrong-password')).toBeNull()
  })

  it('returns null for an unknown email rather than throwing', async () => {
    expect(await verifyPassword('nobody@example.com', 'anything')).toBeNull()
  })
})

describe('issueSession / resolveSession', () => {
  it('issues a session whose raw token resolves back to the user, but the stored hash does not', async () => {
    const user = await registerUser({ email: 'session-check@example.com', password: 'p' })

    const session = await issueSession(user.id)
    const resolved = await resolveSession(session.rawToken)
    expect(resolved?.id).toBe(user.id)

    const rows = await db.execute(sql`select token from control_plane.sessions where user_id = ${user.id}`)
    expect(rows.rows[0].token).not.toBe(session.rawToken)
  })

  it('returns null for an unknown or malformed token', async () => {
    expect(await resolveSession('not-a-real-token')).toBeNull()
  })
})
```

- [ ] **Step 3: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/auth-service.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement the service**

Create `apps/control-plane/server/utils/auth-service.ts` per the interfaces above, using
`@node-rs/argon2`'s `hash`/`verify` for passwords and Node's `crypto.randomBytes`/`crypto.createHash('sha256')`
for sessions.

- [ ] **Step 5: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/auth-service.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 6: Write the route handlers**

Create `apps/control-plane/server/api/auth/register.post.ts`:

```ts
import { z } from 'zod'
import { defineEventHandler, readBody, createError, setCookie } from 'h3'
import { registerUser, issueSession } from '../../utils/auth-service.js'

const RegisterSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  name: z.string().min(1).optional(),
})

export default defineEventHandler(async (event) => {
  const parsed = RegisterSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid registration' })
  }
  const user = await registerUser(parsed.data)
  const session = await issueSession(user.id)
  setCookie(event, 'session_token', session.rawToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    expires: session.expiresAt,
    path: '/',
  })
  return user
})
```

Create `apps/control-plane/server/api/auth/login.post.ts` following the same shape, calling
`verifyPassword` instead of `registerUser`, returning `401` (`createError({ statusCode: 401,
statusMessage: 'Invalid email or password' })`) when it returns `null`.

- [ ] **Step 7: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/package.json apps/control-plane/server/utils/auth-service.ts apps/control-plane/server/api/auth apps/control-plane/test/auth-service.test.ts package-lock.json
git commit -m "feat: add email/password registration, login, and session issuance"
```

---

### Task 3: Authorization guards — `requireSession`, `requireProjectRole`

**Files:**
- Create: `apps/control-plane/server/utils/auth-guard.ts`
- Create: `apps/control-plane/test/auth-guard.test.ts`

**Interfaces:**
- Consumes: `resolveSession` (Task 2); `getDb()` (Stage 3) to look up a `memberships` row.
- Produces (consumed by every route modified in Task 4):
  ```ts
  export const ROLE_RANK: Record<'OWNER' | 'MAINTAINER' | 'MEMBER', number> // OWNER: 2, MAINTAINER: 1, MEMBER: 0
  export async function requireSession(event: H3Event): Promise<AuthenticatedUser>   // throws 401
  export async function requireProjectRole(event: H3Event, projectId: string, minRole: 'OWNER' | 'MAINTAINER' | 'MEMBER'): Promise<AuthenticatedUser>  // throws 401 or 403
  ```
- `requireSession` reads the `session_token` cookie via `getCookie(event, 'session_token')`, calls
  `resolveSession`, and throws `createError({ statusCode: 401, statusMessage: 'Not authenticated' })`
  if there is no cookie or `resolveSession` returns `null`. It returns the resolved
  `AuthenticatedUser` so callers don't need a second lookup.
- `requireProjectRole` calls `requireSession` first (so an unauthenticated caller gets 401, not 403),
  then looks up `memberships` for `(userId, projectId)`. No membership row → 403
  (`createError({ statusCode: 403, statusMessage: 'Not a member of this project' })`). A membership
  row whose `ROLE_RANK[role] < ROLE_RANK[minRole]` → 403 with the same message shape. Otherwise
  returns the `AuthenticatedUser`.

- [ ] **Step 1: Write the failing test**

Create `apps/control-plane/test/auth-guard.test.ts` — this test calls `requireSession`/
`requireProjectRole` with a hand-built minimal H3-event-shaped object (not a full Nitro request) that
only implements what these two functions read (`event.headers` / whatever `getCookie` needs — check
`h3`'s `getCookie` implementation to build a valid enough fake, or use `h3`'s own `createEvent`
test helper if one exists in the installed `h3` version; if neither is practical, use the same
`@nuxt/test-utils/e2e` HTTP-level approach as Stage 3's Task 6/7 instead — decide based on what `h3`
actually exposes, don't force a fragile hand-rolled event object if the real library makes this
harder than a real HTTP round-trip):

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { setup, $fetch } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'
// This test exercises requireSession/requireProjectRole through two minimal throwaway routes
// registered only for this test's purposes is NOT an option (this plan does not add test-only
// routes to the real app) - instead, drive the guards through Task 4's real routes once Task 4
// lands. Reorder: implement this task's two functions with unit-level tests against directly
// constructed fake events if h3's own test utilities support it cleanly; otherwise, fold this
// task's acceptance testing into Task 5's e2e suite (which already covers 401/403/role-gated
// scenarios end to end) and keep this task's own test file scoped to what can be verified without
// a real HTTP server: ROLE_RANK's ordering, and requireSession/requireProjectRole's *rejection*
// paths when resolveSession/getDb are given inputs that can never match (e.g. an empty cookie
// string, a random unresolvable projectId) - both of these paths don't require a live H3 request
// object, only a real Postgres connection via test/support/postgres.ts, since requireSession's
// unauthenticated-cookie branch and requireProjectRole's no-membership-row branch are pure DB
// lookups that fail regardless of what event shape is passed.
```

Write the actual test code once you've resolved which of the two approaches above `h3`'s installed
version supports cleanly — inspect `node_modules/h3/dist/index.d.ts` for a test-friendly
`createEvent`/`mockEvent` export before deciding. Do not leave this decision unresolved in the final
code; the prose above describes the fork, not a license to skip writing real assertions.

- [ ] **Step 2: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/auth-guard.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the guards**

Create `apps/control-plane/server/utils/auth-guard.ts` per the interfaces above.

- [ ] **Step 4: Run test, verify GREEN, and root gates**

Run: `cd apps/control-plane && npx vitest run test/auth-guard.test.ts` then root gates.

```bash
git add apps/control-plane/server/utils/auth-guard.ts apps/control-plane/test/auth-guard.test.ts
git commit -m "feat: add requireSession and requireProjectRole authorization guards"
```

---

### Task 4: Wire guards into every Stage 3 route

**Files:**
- Modify every route listed under "File structure" above except `server/api/auth/*` (Task 2's own
  routes are intentionally unauthenticated).

**Interfaces:**
- Consumes: `requireSession`, `requireProjectRole` (Task 3).

- [ ] **Step 1: Project routes**

Modify `server/api/projects/index.get.ts` and `index.post.ts`: add `await requireSession(event)` as
the handler's first statement (list/register require *a* session, not membership in any particular
project — anyone with an account may register a new project and see the project list; scoping
project visibility to membership is a reasonable future refinement, explicitly out of scope here per
Global Constraints' minimal role mapping).

Modify `server/api/projects/[projectId]/index.get.ts`: replace the bare handler body's start with
`await requireProjectRole(event, getRouterParam(event, 'projectId')!, 'MEMBER')`.

- [ ] **Step 2: Issue routes**

Modify `server/api/projects/[projectId]/issues/index.get.ts` and `index.post.ts`:
`requireProjectRole(event, getRouterParam(event, 'projectId')!, 'MEMBER')`.

Modify `server/api/issues/[issueId]/index.get.ts` and `status.patch.ts`: these are keyed by
`issueId`, not `projectId` — call `getIssue(issueId)` first to learn its `projectId`, return 404 if
null (existing behavior, unchanged), then `requireProjectRole(event, issue.projectId, 'MEMBER')`
before proceeding.

- [ ] **Step 3: Document routes**

Modify `server/api/issues/[issueId]/documents/index.post.ts`: same issue-then-project-role pattern as
Step 2, `'MEMBER'`.

Modify `server/api/documents/[documentId]/revisions/index.post.ts`: this route only has a
`documentId`, not a `projectId` or `issueId` directly — add a `getDocumentProjectId(documentId):
Promise<string | null>` helper to `document-service.ts` (a small, focused addition — one `select`
joining nothing, just reading `documents.projectId` by id) and use it the same way, `'MEMBER'`.

Modify `server/api/document-revisions/[revisionId]/approve.post.ts`: needs the *revision's*
document's project — add `getRevisionProjectId(revisionId): Promise<string | null>` to
`document-service.ts` similarly, then `requireProjectRole(event, projectId, 'MAINTAINER')` (not
`'MEMBER'` — this is the one route in this plan requiring the higher role, per Global Constraints).

- [ ] **Step 4: Notification routes**

Modify `server/api/projects/[projectId]/notifications/index.get.ts`, `unread-count.get.ts`,
`read.post.ts`: `requireProjectRole(event, getRouterParam(event, 'projectId')!, 'MEMBER')`.

Modify `server/api/projects/[projectId]/notifications/stream.get.ts`: the browser's native
`EventSource` cannot set custom headers, but it does send cookies automatically on same-origin
requests — so this route can still call `requireProjectRole` reading the `session_token` cookie the
same way every other route does; no special-casing needed. Add the guard call as the first line of
the handler, before the existing `Last-Event-ID` header logic.

- [ ] **Step 5: Update every existing Stage 3 test that calls a now-guarded route**

Each of `test/project-service.test.ts`, `test/issue-service.test.ts`, `test/document-service.test.ts`,
`test/notification-service.test.ts`, `test/notification-stream.test.ts`, `test/api.e2e.test.ts` either
(a) calls the *service* functions directly (unaffected — guards live in route handlers, not services,
per this plan's Architecture) or (b) calls a route via `$fetch`/HTTP (Stage 3's `notification-stream`
and `api.e2e` suites) — for (b), each such test must now register+login a user first and thread the
returned `Set-Cookie` header into subsequent requests, or it will start failing with 401. Update those
two test files' setup to do so; do not leave them broken by this task's own change. Do NOT weaken any
route's guard to keep an old test passing without a session — fix the test.

- [ ] **Step 6: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck` — expect the two updated
Stage 3 HTTP-level test files to still pass (with the added login step), and every service-level test
to be unaffected.

```bash
git add apps/control-plane/server
git commit -m "feat: require session and project-role authorization on all control plane routes"
```

---

### Task 5: End-to-end auth/authorization test + analysis doc

**Files:**
- Create: `apps/control-plane/test/auth.e2e.test.ts`
- Create: `docs/03-analysis/nuxt-stage4-control-plane-auth.analysis.md`

**Interfaces:**
- Consumes: `POST /api/auth/register`, `POST /api/auth/login` (Task 2); any Stage 3 route now guarded
  by Task 4.

- [ ] **Step 1: Write the end-to-end test**

Create `apps/control-plane/test/auth.e2e.test.ts` using the same `@nuxt/test-utils/e2e` `setup()`
pattern as Stage 3's `api.e2e.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { setup, $fetch } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'

await setup({ rootDir: fileURLToPath(new URL('..', import.meta.url)) })

describe('Control Plane authentication and authorization', () => {
  it('rejects an unauthenticated request to a protected route with 401', async () => {
    await expect($fetch('/api/projects')).rejects.toMatchObject({ response: { status: 401 } })
  })

  it('rejects a request to a project the caller is not a member of with 403', async () => {
    const owner = await registerAndLogin('owner@example.com')
    const outsider = await registerAndLogin('outsider@example.com')

    const project = await $fetch('/api/projects', {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { name: 'Private project', repositoryUri: 'https://github.com/acme/private.git' },
    })

    await expect($fetch(`/api/projects/${project.id}`, { headers: outsider.cookieHeader }))
      .rejects.toMatchObject({ response: { status: 403 } })
  })

  it('requires MAINTAINER to approve a document revision - MEMBER is rejected, MAINTAINER succeeds', async () => {
    // Arrange: owner registers a project (becomes OWNER via Task 5's project-service change - see
    // below), invites a second user as MEMBER and a third as MAINTAINER by inserting memberships
    // rows directly (no membership-invitation API exists yet - out of scope, same pattern as Stage
    // 3's insertTestUser direct-DB-insert precedent). File an issue, draft a document as the owner.
    // Assert the MEMBER's approve call is rejected 403; the MAINTAINER's approve call succeeds.
  })
})
```

Write the actual third test's body when you implement this step — the comment describes the
arrangement, not a license to leave it unwritten.

This task also requires `project-service.ts`'s `registerProject` to insert an `OWNER` membership row
for the registering user in the same transaction as the project insert (previously ungated,
Stage 3 had no concept of "who owns this project"). Add this as a small, TDD-covered change to
`project-service.test.ts` and `project-service.ts` within this task (it is a natural extension of
Task 4's wiring, not a new domain concern, so it belongs here rather than a separate task) — write
the failing assertion first (`registerProject` followed by a direct query of
`control_plane.memberships` for that `(userId, projectId)` expecting `role === 'OWNER'`), confirm RED,
then implement.

- [ ] **Step 2: Run test, verify RED then GREEN**

Run: `cd apps/control-plane && npx vitest run test/auth.e2e.test.ts`
Expected: fails first for genuine reasons, fix forward to green. Do not skip or stub any assertion.

- [ ] **Step 3: Write the Stage 4 analysis doc**

Create `docs/03-analysis/nuxt-stage4-control-plane-auth.analysis.md` following the style of
`docs/03-analysis/nuxt-stage3-control-plane-core-api.analysis.md` — acceptance-criteria table
(unauthenticated 401, cross-project 403, role-gated approval), what was verified against real
Postgres, any deviations, and remaining scope (OAuth/OIDC, membership-invitation API, screen wiring in
Stage 7).

- [ ] **Step 4: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/test/auth.e2e.test.ts apps/control-plane/server/utils/project-service.ts apps/control-plane/test/project-service.test.ts docs/03-analysis/nuxt-stage4-control-plane-auth.analysis.md
git commit -m "test: verify control plane authentication and authorization end to end"
```

---

## Self-Review Notes (completed by the plan author before handoff)

- **Spec coverage:** Session cookies (Task 2), user/project membership (Task 1 schema + Task 5's
  OWNER-on-register wiring), server-side authorization on every API route (Task 3-4), and the three
  named test scenarios (401/403/role-gated) (Task 5) — all of Stage 4's stated scope is covered.
  OAuth/OIDC is explicitly out of scope per Global Constraints.
- **Placeholder scan:** Task 3's guard test and Task 5's third e2e test both flag a genuine open
  implementation decision (which `h3` testing primitive is available; the exact membership-seeding
  arrangement) rather than hand-waving past it — both are called out explicitly as decisions the
  implementer must resolve and *then write real code for*, matching Stage 3 plan's precedent for its
  one similar exception (the SSE test bodies).
- **Type consistency:** `AuthenticatedUser`/`IssuedSession` (Task 2) are consumed unchanged by Task
  3's guard signatures; `ROLE_RANK`'s three role strings match Global Constraints' role model and
  Task 5's membership-seeding test exactly.
