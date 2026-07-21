# Nuxt Control Plane Core API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Stage 3 of `docs/superpowers/plans/2026-07-20-nuxt-control-plane-temporal-migration.md` — Project/Issue CRUD, Document revision/approval, Notification read/replay, and transactional outbox, as Nitro API routes in `apps/control-plane` backed by `@agentic-worker/db`'s `control_plane` schema.

**Architecture:** Each domain gets a thin Nitro route layer (`server/api/**`) over a pure, directly-testable service module (`server/utils/*-service.ts`) that talks to Drizzle. Every write that must notify other consumers (Issue creation, Document revision approval) inserts an `outbox_events` row in the *same* DB transaction as the domain change — no publisher runs inside that transaction, matching the migration plan's transactional-outbox rule. No authentication yet (Stage 4) — every route is open.

**Tech Stack:** Nuxt 4 / Nitro (H3 event handlers), Drizzle ORM (`@agentic-worker/db`), `pg`, Zod (request validation, reusing `@agentic-worker/contracts` conventions), Vitest, `@nuxt/test-utils` for HTTP-level integration tests, real PostgreSQL via Docker (`postgres:16-alpine`) for both unit-level service tests and the end-to-end API test.

## Global Constraints

- Every domain write to a table that other consumers observe (Issue creation, Document revision
  approval) must insert its `outbox_events` row inside the same `db.transaction()` as the domain
  change — copied verbatim from the migration plan's `## 트랜잭션·알림·인증 규칙`.
- No route in this plan performs authentication or authorization — Stage 4 adds that layer on top.
  Do not add session/cookie checks here.
- `credentialRef` must never appear in any Project API response — copied from the Java Control Plane
  invariant this migration preserves (`docs/01-plan/control-plane/control-plane-master-plan.md` §
  Cross-plan invariants).
- A `repositoryUri` must be `https`, `http`, or `ssh` — never a filesystem path — same invariant,
  enforced with the existing `WorkRequestedSchema`-style loose-UUID/remote-URI Zod pattern from
  `packages/contracts/src/work-requested.ts` (reuse the same regex approach, do not re-invent).
- `document_revisions` rows are immutable once created — approval is a status change on the existing
  row (`approvedAt`/`approvedByUserId`), never a new revision, never an UPDATE to `content`.
- TDD throughout: a failing Vitest test before its implementation, for every task.
- One task = one commit. Do not mix domains in a single commit.
- Do not modify anything under `src/main/java`, `contracts/`, `control-plane-app/`,
  `agent-engine-app/`, `frontend/`, `apps/temporal-worker/`, or `packages/contracts/` — this plan only
  touches `apps/control-plane/` and, for the one schema addition in Task 4, `packages/db/`.
- Root gates must stay green after every task: `npm run test`, `npm run lint`, `npm run typecheck`
  (run from the repo root, not just the workspace).

---

## File structure

```
apps/control-plane/
  nuxt.config.ts                          -- add runtimeConfig.databaseUrl (Modify)
  server/
    utils/
      db.ts                                -- singleton Drizzle client (Task 1)
      outbox.ts                            -- withOutbox(tx, event) helper (Task 1)
      project-service.ts                   -- Task 2
      issue-service.ts                     -- Task 3
      document-service.ts                  -- Task 4
      notification-service.ts              -- Task 5 + 6
    api/
      projects/
        index.get.ts                       -- Task 2
        index.post.ts                      -- Task 2
        [projectId]/
          index.get.ts                     -- Task 2
          issues/
            index.get.ts                   -- Task 3
            index.post.ts                  -- Task 3
          notifications/
            index.get.ts                   -- Task 5
            unread-count.get.ts             -- Task 5
            read.post.ts                    -- Task 5
            stream.get.ts                   -- Task 6
      issues/
        [issueId]/
          index.get.ts                     -- Task 3
          status.patch.ts                  -- Task 3
          documents/
            index.post.ts                  -- Task 4
      documents/
        [documentId]/
          revisions/
            index.post.ts                  -- Task 4
      document-revisions/
        [revisionId]/
          approve.post.ts                  -- Task 4
  test/
    project-service.test.ts                -- Task 2
    issue-service.test.ts                  -- Task 3
    document-service.test.ts               -- Task 4
    notification-service.test.ts           -- Task 5 + 6
    api.e2e.test.ts                        -- Task 7 (@nuxt/test-utils, golden path)

packages/db/
  src/control-plane/document-revisions.ts  -- Modify: add approvedAt/approvedByUserId (Task 4)
  drizzle/control-plane/000X_*.sql         -- Modify: regenerate via drizzle-kit generate (Task 4)
```

---

### Task 1: DB client and transactional outbox helper

**Files:**
- Create: `apps/control-plane/server/utils/db.ts`
- Create: `apps/control-plane/server/utils/outbox.ts`
- Create: `apps/control-plane/test/outbox.test.ts`
- Modify: `apps/control-plane/nuxt.config.ts`
- Modify: `apps/control-plane/package.json` (add `@agentic-worker/db`, `pg`, `@types/pg`, `@nuxt/test-utils` deps)

**Interfaces:**
- Produces: `getDb(): NodePgDatabase<typeof controlPlaneSchemaExports>` — a lazily-created singleton
  Drizzle client, from `apps/control-plane/server/utils/db.ts`. Later tasks call `getDb()` and never
  construct their own `Pool`/`drizzle()` instance.
- Produces: `withOutbox<T>(tx: NodePgDatabase, event: { aggregateType: string, aggregateId: string, eventType: string, payload: unknown }, work: (tx) => Promise<T>): Promise<T>` — runs `work` and inserts one `outboxEvents` row in the same transaction, returning `work`'s result. From `apps/control-plane/server/utils/outbox.ts`.

- [ ] **Step 1: Add dependencies**

Modify `apps/control-plane/package.json`:

```json
{
  "dependencies": {
    "@agentic-worker/contracts": "*",
    "@agentic-worker/db": "*",
    "nuxt": "^4.5.0",
    "pg": "^8.13.1",
    "vue": "^3.5.0"
  },
  "devDependencies": {
    "@nuxt/test-utils": "^3.19.0",
    "@types/pg": "^8.11.10",
    "typescript": "^5.7.2",
    "vue-tsc": "^2.2.0"
  }
}
```

Run `npm install` from the repo root afterward (workspaces).

- [ ] **Step 2: Add `databaseUrl` runtime config**

Modify `apps/control-plane/nuxt.config.ts`:

```ts
export default defineNuxtConfig({
  compatibilityDate: '2026-07-20',
  devtools: { enabled: true },
  runtimeConfig: {
    databaseUrl: process.env.DATABASE_URL || 'postgresql://dev_user:dev_password@localhost:15432/agentic_worker',
  },
})
```

- [ ] **Step 3: Write the DB client**

Create `apps/control-plane/server/utils/db.ts`:

```ts
import { drizzle, type NodePgDatabase } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import * as controlPlane from '@agentic-worker/db/control-plane'
import { useRuntimeConfig } from '#imports'

let instance: NodePgDatabase<typeof controlPlane> | null = null

export function getDb(): NodePgDatabase<typeof controlPlane> {
  if (!instance) {
    const pool = new Pool({ connectionString: useRuntimeConfig().databaseUrl })
    instance = drizzle(pool, { schema: controlPlane })
  }
  return instance
}
```

Check first whether `@agentic-worker/db` exposes a `./control-plane` subpath export (look at
`packages/db/package.json` and `packages/db/src/index.ts`) — if it currently only exports one
flattened `index.ts` with `controlPlane`/`engine` namespaces, import from there instead
(`import { controlPlane } from '@agentic-worker/db'`) and adjust the `schema:` argument to
`controlPlane` accordingly. Do not add a new subpath export to `packages/db` for this — use what
Stage 2 already produced.

- [ ] **Step 4: Write the failing outbox test**

Create `apps/control-plane/test/outbox.test.ts`:

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { GenericContainer, type StartedTestContainer } from 'testcontainers'
import { drizzle } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { sql } from 'drizzle-orm'
import * as controlPlane from '@agentic-worker/db/control-plane'
import { withOutbox } from '../server/utils/outbox.js'

let container: StartedTestContainer
let pool: Pool
let db: ReturnType<typeof drizzle<typeof controlPlane>>

beforeAll(async () => {
  container = await new GenericContainer('postgres:16-alpine')
    .withEnvironment({ POSTGRES_PASSWORD: 'test', POSTGRES_DB: 'test' })
    .withExposedPorts(5432)
    .start()
  pool = new Pool({
    host: container.getHost(),
    port: container.getMappedPort(5432),
    user: 'postgres',
    password: 'test',
    database: 'test',
  })
  db = drizzle(pool, { schema: controlPlane })
  await pool.query('CREATE SCHEMA IF NOT EXISTS control_plane')
  await pool.query(`CREATE TABLE control_plane.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz
  )`)
}, 60_000)

afterAll(async () => {
  await pool.end()
  await container.stop()
})

describe('withOutbox', () => {
  it('inserts one outbox row in the same transaction as the wrapped work', async () => {
    const aggregateId = '11111111-1111-1111-1111-111111111111'

    const result = await withOutbox(
      db,
      { aggregateType: 'issue', aggregateId, eventType: 'ISSUE_CREATED', payload: { title: 'test' } },
      async () => 'work-result',
    )

    expect(result).toBe('work-result')
    const rows = await db.execute(sql`select * from control_plane.outbox_events`)
    expect(rows.rows).toHaveLength(1)
    expect(rows.rows[0].event_type).toBe('ISSUE_CREATED')
  })

  it('rolls back the outbox insert if the wrapped work throws', async () => {
    await expect(withOutbox(
      db,
      { aggregateType: 'issue', aggregateId: '22222222-2222-2222-2222-222222222222', eventType: 'X', payload: {} },
      async () => { throw new Error('boom') },
    )).rejects.toThrow('boom')

    const rows = await db.execute(sql`select * from control_plane.outbox_events where event_type = 'X'`)
    expect(rows.rows).toHaveLength(0)
  })
})
```

Add `testcontainers` as a devDependency of `apps/control-plane` if not already present at the repo
root (`packages/db` already uses it — check `packages/db/package.json`; if it's hoisted at the root
`node_modules` via npm workspaces you may not need to add it again, but declare it explicitly in
`apps/control-plane/package.json` regardless, since each workspace declares its own direct deps).

- [ ] **Step 5: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/outbox.test.ts`
Expected: FAIL — `../server/utils/outbox.js` does not exist.

- [ ] **Step 6: Implement `withOutbox`**

Create `apps/control-plane/server/utils/outbox.ts`:

```ts
import type { NodePgDatabase } from 'drizzle-orm/node-postgres'
import * as controlPlane from '@agentic-worker/db/control-plane'

export interface OutboxEvent {
  aggregateType: string
  aggregateId: string
  eventType: string
  payload: unknown
}

export async function withOutbox<T>(
  db: NodePgDatabase<typeof controlPlane>,
  event: OutboxEvent,
  work: (tx: NodePgDatabase<typeof controlPlane>) => Promise<T>,
): Promise<T> {
  return db.transaction(async (tx) => {
    const result = await work(tx)
    await tx.insert(controlPlane.outboxEvents).values({
      aggregateType: event.aggregateType,
      aggregateId: event.aggregateId,
      eventType: event.eventType,
      payload: event.payload,
    })
    return result
  })
}
```

Adjust the `@agentic-worker/db/control-plane` import path to match whatever Step 3 settled on.

- [ ] **Step 7: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/outbox.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 8: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`
Expected: all green, no regression in `packages/contracts`, `packages/db`, `apps/temporal-worker`.

```bash
git add apps/control-plane/package.json apps/control-plane/nuxt.config.ts apps/control-plane/server/utils/db.ts apps/control-plane/server/utils/outbox.ts apps/control-plane/test/outbox.test.ts package-lock.json
git commit -m "feat: add control-plane DB client and transactional outbox helper"
```

---

### Task 2: Project registration and listing API

**Files:**
- Create: `apps/control-plane/server/utils/project-service.ts`
- Create: `apps/control-plane/server/api/projects/index.get.ts`
- Create: `apps/control-plane/server/api/projects/index.post.ts`
- Create: `apps/control-plane/server/api/projects/[projectId]/index.get.ts`
- Create: `apps/control-plane/test/project-service.test.ts`

**Interfaces:**
- Consumes: `getDb()` from Task 1.
- Produces (from `project-service.ts`, consumed by later tasks' route handlers and by Task 3's
  Issue service to validate a project exists):
  ```ts
  export interface RegisterProjectInput {
    name: string
    repositoryUri: string      // must be https/http/ssh — validated by the route, not the service
    baseBranch?: string        // defaults to 'main'
    credentialRef?: string
  }
  export interface ProjectView {
    id: string
    name: string
    repositoryUri: string | null
    baseBranch: string
    createdAt: string          // ISO 8601
    // credentialRef is intentionally NOT in this type — never returned
  }
  export async function registerProject(input: RegisterProjectInput): Promise<ProjectView>
  export async function listProjects(): Promise<ProjectView[]>
  export async function getProject(projectId: string): Promise<ProjectView | null>
  ```

- [ ] **Step 1: Write the failing service test**

Create `apps/control-plane/test/project-service.test.ts` (reuse the same `GenericContainer` +
schema-bootstrap pattern from Task 1's `test/outbox.test.ts` — extract a shared
`test/support/postgres.ts` helper exporting `startTestDatabase()`/`stopTestDatabase()` if that
duplication would otherwise appear a third time in Task 3; for this task, duplicating it once more
is fine per YAGNI):

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { registerProject, listProjects, getProject } from '../server/utils/project-service.js'
// ... same container bootstrap as Task 1, but CREATE TABLE control_plane.projects matching
// packages/db/src/control-plane/projects.ts's exact columns (id uuid pk default gen_random_uuid(),
// name text not null, local_path text, base_branch text not null default 'main',
// repository_uri text, credential_ref text, created_at timestamptz not null default now(),
// unique index on repository_uri where not null) instead of outbox_events.

describe('registerProject', () => {
  it('persists a project and never returns credentialRef', async () => {
    const project = await registerProject({
      name: 'Catalog Service',
      repositoryUri: 'https://github.com/acme/catalog.git',
      credentialRef: 'secret-ref-should-not-leak',
    })

    expect(project.name).toBe('Catalog Service')
    expect(project.baseBranch).toBe('main')
    expect(project).not.toHaveProperty('credentialRef')
  })
})

describe('listProjects / getProject', () => {
  it('lists registered projects and fetches one by id', async () => {
    const created = await registerProject({ name: 'Second', repositoryUri: 'https://github.com/acme/second.git' })

    const all = await listProjects()
    expect(all.some((p) => p.id === created.id)).toBe(true)

    const fetched = await getProject(created.id)
    expect(fetched?.name).toBe('Second')
  })

  it('returns null for an unknown project id', async () => {
    expect(await getProject('00000000-0000-0000-0000-000000000000')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/project-service.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the service**

Create `apps/control-plane/server/utils/project-service.ts`:

```ts
import { eq } from 'drizzle-orm'
import * as controlPlane from '@agentic-worker/db/control-plane'
import { getDb } from './db.js'

export interface RegisterProjectInput {
  name: string
  repositoryUri: string
  baseBranch?: string
  credentialRef?: string
}

export interface ProjectView {
  id: string
  name: string
  repositoryUri: string | null
  baseBranch: string
  createdAt: string
}

function toView(row: typeof controlPlane.projects.$inferSelect): ProjectView {
  return {
    id: row.id,
    name: row.name,
    repositoryUri: row.repositoryUri,
    baseBranch: row.baseBranch,
    createdAt: row.createdAt.toISOString(),
  }
}

export async function registerProject(input: RegisterProjectInput): Promise<ProjectView> {
  const [row] = await getDb().insert(controlPlane.projects).values({
    name: input.name,
    repositoryUri: input.repositoryUri,
    baseBranch: input.baseBranch ?? 'main',
    credentialRef: input.credentialRef ?? null,
  }).returning()
  return toView(row)
}

export async function listProjects(): Promise<ProjectView[]> {
  const rows = await getDb().select().from(controlPlane.projects)
  return rows.map(toView)
}

export async function getProject(projectId: string): Promise<ProjectView | null> {
  const [row] = await getDb().select().from(controlPlane.projects).where(eq(controlPlane.projects.id, projectId))
  return row ? toView(row) : null
}
```

- [ ] **Step 4: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/project-service.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Write the route handlers**

Create `apps/control-plane/server/api/projects/index.post.ts`:

```ts
import { z } from 'zod'
import { defineEventHandler, readBody, createError } from '#imports'
import { registerProject } from '../../utils/project-service.js'

const remoteRepositoryUri = z.string().url().refine(
  (value) => /^(https|http|ssh):\/\//.test(value),
  { message: 'repositoryUri must be https, http, or ssh' },
)

const RegisterProjectSchema = z.object({
  name: z.string().min(1),
  repositoryUri: remoteRepositoryUri,
  baseBranch: z.string().min(1).optional(),
  credentialRef: z.string().min(1).optional(),
})

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const parsed = RegisterProjectSchema.safeParse(body)
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid project' })
  }
  return registerProject(parsed.data)
})
```

Create `apps/control-plane/server/api/projects/index.get.ts`:

```ts
import { defineEventHandler } from '#imports'
import { listProjects } from '../../utils/project-service.js'

export default defineEventHandler(() => listProjects())
```

Create `apps/control-plane/server/api/projects/[projectId]/index.get.ts`:

```ts
import { defineEventHandler, getRouterParam, createError } from '#imports'
import { getProject } from '../../../utils/project-service.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  const project = await getProject(projectId)
  if (!project) {
    throw createError({ statusCode: 404, statusMessage: 'Project not found' })
  }
  return project
})
```

- [ ] **Step 6: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/server apps/control-plane/test/project-service.test.ts
git commit -m "feat: add Project registration and listing API"
```

---

### Task 3: Issue creation, listing, and status API

**Files:**
- Create: `apps/control-plane/server/utils/issue-service.ts`
- Create: `apps/control-plane/server/api/projects/[projectId]/issues/index.get.ts`
- Create: `apps/control-plane/server/api/projects/[projectId]/issues/index.post.ts`
- Create: `apps/control-plane/server/api/issues/[issueId]/index.get.ts`
- Create: `apps/control-plane/server/api/issues/[issueId]/status.patch.ts`
- Create: `apps/control-plane/test/issue-service.test.ts`

**Interfaces:**
- Consumes: `getDb()`, `withOutbox()` (Task 1); `getProject()` (Task 2, to validate the project
  exists before creating an Issue).
- Produces (consumed by Task 4's document creation route, which needs an issue to exist):
  ```ts
  export interface CreateIssueInput {
    title: string
    description?: string
    priority?: string
  }
  export interface IssueView {
    id: string
    projectId: string
    issueNumber: number
    title: string
    description: string | null
    priority: string | null
    status: string
    createdAt: string
  }
  export async function createIssue(projectId: string, input: CreateIssueInput): Promise<IssueView>
  export async function listIssuesByProject(projectId: string): Promise<IssueView[]>
  export async function getIssue(issueId: string): Promise<IssueView | null>
  export async function updateIssueStatus(issueId: string, status: string): Promise<IssueView | null>
  ```
- `issueNumber` is assigned as `1 + max(existing issueNumber for this project)`, computed inside the
  same transaction as the insert (matching the Java `issues_project_id_issue_number_unique`
  constraint) — read the current max with a `SELECT ... FOR UPDATE`-style guard is unnecessary at
  this scale; rely on the existing `unique(project_id, issue_number)` constraint and retry-once on a
  unique-violation race (23505) by recomputing the max and re-inserting. Do not add advisory locks —
  out of scope for this task.
- `createIssue` publishes one outbox event: `{ aggregateType: 'issue', aggregateId: <issue id>,
  eventType: 'ISSUE_CREATED', payload: { projectId, issueNumber, title } }`.

- [ ] **Step 1: Write the failing service test**

Create `apps/control-plane/test/issue-service.test.ts` — same container-bootstrap pattern, this time
creating both `control_plane.projects` and `control_plane.issues` (and `control_plane.outbox_events`,
since `createIssue` writes to it) tables matching their Drizzle definitions:

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { sql } from 'drizzle-orm'
import { createIssue, listIssuesByProject, getIssue, updateIssueStatus } from '../server/utils/issue-service.js'
// ... container bootstrap identical in shape to project-service.test.ts, plus:
// CREATE TABLE control_plane.issues (id uuid pk default gen_random_uuid(), project_id uuid not null
//   references control_plane.projects(id), issue_number integer not null, title text not null,
//   description text, priority text, status text not null default 'OPEN',
//   created_at timestamptz not null default now(),
//   unique(project_id, issue_number))
// and a helper `insertTestProject(db)` that inserts one row directly into control_plane.projects
// and returns its id (do not go through project-service.ts here - keep this test scoped to issues).

describe('createIssue', () => {
  it('assigns issueNumber 1 for the first issue in a project, 2 for the second', async () => {
    const projectId = await insertTestProject(db)

    const first = await createIssue(projectId, { title: 'First issue' })
    const second = await createIssue(projectId, { title: 'Second issue' })

    expect(first.issueNumber).toBe(1)
    expect(second.issueNumber).toBe(2)
    expect(first.status).toBe('OPEN')
  })

  it('writes one ISSUE_CREATED outbox row per created issue', async () => {
    const projectId = await insertTestProject(db)
    const issue = await createIssue(projectId, { title: 'Outbox check' })

    const rows = await db.execute(sql`select * from control_plane.outbox_events where aggregate_id = ${issue.id}`)
    expect(rows.rows).toHaveLength(1)
    expect(rows.rows[0].event_type).toBe('ISSUE_CREATED')
  })
})

describe('listIssuesByProject / getIssue / updateIssueStatus', () => {
  it('lists issues for a project and updates status', async () => {
    const projectId = await insertTestProject(db)
    const issue = await createIssue(projectId, { title: 'Status flow' })

    const list = await listIssuesByProject(projectId)
    expect(list.map((i) => i.id)).toContain(issue.id)

    const updated = await updateIssueStatus(issue.id, 'IN_PROGRESS')
    expect(updated?.status).toBe('IN_PROGRESS')

    const fetched = await getIssue(issue.id)
    expect(fetched?.status).toBe('IN_PROGRESS')
  })

  it('returns null updating an unknown issue', async () => {
    expect(await updateIssueStatus('00000000-0000-0000-0000-000000000000', 'DONE')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/issue-service.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the service**

Create `apps/control-plane/server/utils/issue-service.ts`:

```ts
import { and, desc, eq } from 'drizzle-orm'
import * as controlPlane from '@agentic-worker/db/control-plane'
import { getDb } from './db.js'
import { withOutbox } from './outbox.js'

export interface CreateIssueInput {
  title: string
  description?: string
  priority?: string
}

export interface IssueView {
  id: string
  projectId: string
  issueNumber: number
  title: string
  description: string | null
  priority: string | null
  status: string
  createdAt: string
}

function toView(row: typeof controlPlane.issues.$inferSelect): IssueView {
  return {
    id: row.id,
    projectId: row.projectId,
    issueNumber: row.issueNumber,
    title: row.title,
    description: row.description,
    priority: row.priority,
    status: row.status,
    createdAt: row.createdAt.toISOString(),
  }
}

async function nextIssueNumber(tx: typeof controlPlane, projectId: string): Promise<number> {
  const [last] = await getDb().select({ issueNumber: controlPlane.issues.issueNumber })
    .from(controlPlane.issues)
    .where(eq(controlPlane.issues.projectId, projectId))
    .orderBy(desc(controlPlane.issues.issueNumber))
    .limit(1)
  return (last?.issueNumber ?? 0) + 1
}

export async function createIssue(projectId: string, input: CreateIssueInput): Promise<IssueView> {
  const attempt = async (): Promise<IssueView> => {
    const issueNumber = await nextIssueNumber(controlPlane, projectId)
    return withOutbox(
      getDb(),
      { aggregateType: 'issue', aggregateId: '', eventType: 'ISSUE_CREATED', payload: { projectId, issueNumber, title: input.title } },
      async (tx) => {
        const [row] = await tx.insert(controlPlane.issues).values({
          projectId,
          issueNumber,
          title: input.title,
          description: input.description ?? null,
          priority: input.priority ?? null,
        }).returning()
        return toView(row)
      },
    )
  }

  try {
    return await attempt()
  } catch (error: any) {
    if (error?.code === '23505') return attempt() // unique_violation race on issue_number - retry once
    throw error
  }
}

export async function listIssuesByProject(projectId: string): Promise<IssueView[]> {
  const rows = await getDb().select().from(controlPlane.issues).where(eq(controlPlane.issues.projectId, projectId))
  return rows.map(toView)
}

export async function getIssue(issueId: string): Promise<IssueView | null> {
  const [row] = await getDb().select().from(controlPlane.issues).where(eq(controlPlane.issues.id, issueId))
  return row ? toView(row) : null
}

export async function updateIssueStatus(issueId: string, status: string): Promise<IssueView | null> {
  const [row] = await getDb().update(controlPlane.issues).set({ status })
    .where(eq(controlPlane.issues.id, issueId)).returning()
  return row ? toView(row) : null
}
```

Note the `outboxEvents.aggregateId` in `withOutbox`'s call above is set to `''` before the row's real
id exists yet (it's generated inside `work`). Fix this properly in implementation: either (a) generate
the issue `id` client-side with `crypto.randomUUID()` before the transaction so it's known upfront and
pass it as both the inserted `id` and the outbox `aggregateId`, or (b) insert the outbox row *after*
computing the domain row inside `work`, using a two-phase insert within the same `withOutbox`
transaction. Prefer (a) — simpler, and matches `documents`/`document_revisions`' existing
`uuid().defaultRandom()` column shape (a client-generated UUID is still a valid explicit `.values({ id
: ... })`). Update `createIssue` accordingly before writing this step's code for real; the pseudocode
above is illustrative of the retry-on-23505 shape, not final.

- [ ] **Step 4: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/issue-service.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 5: Write the route handlers**

Create `apps/control-plane/server/api/projects/[projectId]/issues/index.post.ts`:

```ts
import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from '#imports'
import { createIssue } from '../../../../utils/issue-service.js'
import { getProject } from '../../../../utils/project-service.js'

const CreateIssueSchema = z.object({
  title: z.string().min(1),
  description: z.string().optional(),
  priority: z.string().optional(),
})

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  if (!(await getProject(projectId))) {
    throw createError({ statusCode: 404, statusMessage: 'Project not found' })
  }
  const parsed = CreateIssueSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid issue' })
  }
  return createIssue(projectId, parsed.data)
})
```

Create `apps/control-plane/server/api/projects/[projectId]/issues/index.get.ts`:

```ts
import { defineEventHandler, getRouterParam } from '#imports'
import { listIssuesByProject } from '../../../../utils/issue-service.js'

export default defineEventHandler((event) => listIssuesByProject(getRouterParam(event, 'projectId')!))
```

Create `apps/control-plane/server/api/issues/[issueId]/index.get.ts`:

```ts
import { defineEventHandler, getRouterParam, createError } from '#imports'
import { getIssue } from '../../../utils/issue-service.js'

export default defineEventHandler(async (event) => {
  const issue = await getIssue(getRouterParam(event, 'issueId')!)
  if (!issue) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  return issue
})
```

Create `apps/control-plane/server/api/issues/[issueId]/status.patch.ts`:

```ts
import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from '#imports'
import { updateIssueStatus } from '../../../utils/issue-service.js'

const UpdateStatusSchema = z.object({ status: z.string().min(1) })

export default defineEventHandler(async (event) => {
  const parsed = UpdateStatusSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'status is required' })
  }
  const updated = await updateIssueStatus(getRouterParam(event, 'issueId')!, parsed.data.status)
  if (!updated) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  return updated
})
```

- [ ] **Step 6: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/server apps/control-plane/test/issue-service.test.ts
git commit -m "feat: add Issue creation, listing, and status API"
```

---

### Task 4: Document, revision, and approval API (adds approval columns)

**Files:**
- Modify: `packages/db/src/control-plane/document-revisions.ts` (add `approvedAt`, `approvedByUserId`)
- Regenerate: `packages/db/drizzle/control-plane/*` via `drizzle-kit generate`
- Create: `apps/control-plane/server/utils/document-service.ts`
- Create: `apps/control-plane/server/api/issues/[issueId]/documents/index.post.ts`
- Create: `apps/control-plane/server/api/documents/[documentId]/revisions/index.post.ts`
- Create: `apps/control-plane/server/api/document-revisions/[revisionId]/approve.post.ts`
- Create: `apps/control-plane/test/document-service.test.ts`
- Modify: `packages/db/test/schema.test.ts` (extend the existing `document_revisions` describe block)

**Interfaces:**
- Consumes: `getDb()`, `withOutbox()` (Task 1); `getIssue()` (Task 3, to validate the issue exists
  when creating a project- vs issue-scoped document — an issue-scoped document requires
  `getIssue(issueId)` to return non-null; a project-scoped document only requires the project to
  exist, per Task 2's `getProject()`).
- Produces:
  ```ts
  export interface CreateDocumentInput {
    projectId: string
    issueId?: string          // omit for a project-scoped reusable guidance document
    kind: 'PROMPT_TEMPLATE' | 'DEVELOPMENT_GUIDE' | 'QA_GUIDE' | 'PLAN' | 'IMPLEMENTATION_PLAN' | 'DEVELOPMENT_RESULT' | 'QA_REPORT'
    title: string
    content: string            // becomes the document's first revision (revisionNumber = 1)
  }
  export interface DocumentRevisionView {
    id: string
    documentId: string
    revisionNumber: number
    content: string
    approvedAt: string | null
    approvedByUserId: string | null
    createdAt: string
  }
  export interface DocumentView {
    id: string
    projectId: string
    issueId: string | null
    kind: string
    title: string
    createdAt: string
    latestRevision: DocumentRevisionView
  }
  export async function createDocument(input: CreateDocumentInput): Promise<DocumentView>
  export async function addRevision(documentId: string, content: string): Promise<DocumentRevisionView>
  export async function approveRevision(revisionId: string, approvedByUserId: string): Promise<DocumentRevisionView | null>
  ```
- `createDocument` and `addRevision` do not write outbox events (per the plan, only "이슈 생성, 문서
  승인, webhook 수신" require the transactional outbox — document *creation* is not in that list,
  only *approval* is). `approveRevision` publishes `{ aggregateType: 'document_revision',
  aggregateId: <revision id>, eventType: 'DOCUMENT_REVISION_APPROVED', payload: { documentId,
  revisionNumber, approvedByUserId } }`.

- [ ] **Step 1: Add approval columns to the schema (packages/db)**

Modify `packages/db/src/control-plane/document-revisions.ts`:

```ts
import { integer, text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { documents } from './documents.js'
import { users } from './users.js'

/**
 * Append-only content revisions for a document. Edits create a new row with an
 * incremented revision_number; existing rows are never updated except to record approval
 * (approved_at/approved_by_user_id), which is a status change, not a content edit.
 */
export const documentRevisions = controlPlaneSchema.table('document_revisions', {
  id: uuid('id').primaryKey().defaultRandom(),
  documentId: uuid('document_id').notNull().references(() => documents.id),
  revisionNumber: integer('revision_number').notNull(),
  content: text('content').notNull(),
  approvedAt: timestamp('approved_at', { withTimezone: true }),
  approvedByUserId: uuid('approved_by_user_id').references(() => users.id),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('document_revisions_document_id_revision_number_unique')
    .on(table.documentId, table.revisionNumber),
])
```

- [ ] **Step 2: Extend the existing Drizzle schema test (RED first)**

Modify `packages/db/test/schema.test.ts`, inside the existing
`describe('control_plane.document_revisions', ...)` block, add:

```ts
it('supports approval as a status change: approved_at and approved_by_user_id, both nullable', () => {
  const names = columnNames(controlPlane.documentRevisions)
  expect(names).toEqual(expect.arrayContaining(['approved_at', 'approved_by_user_id']))

  const approvedAt = getTableConfig(controlPlane.documentRevisions).columns.find((c) => c.name === 'approved_at')
  const approvedBy = getTableConfig(controlPlane.documentRevisions).columns.find((c) => c.name === 'approved_by_user_id')
  expect(approvedAt?.notNull).toBe(false)
  expect(approvedBy?.notNull).toBe(false)
})
```

Run: `cd packages/db && npx vitest run test/schema.test.ts` — expect this new assertion to FAIL
before Step 1's columns exist (if you did Step 1 first per the listing above, do Step 1 AFTER writing
this test instead, so you see it fail first — re-order your own execution to keep TDD honest: write
this test, confirm RED, then add the columns from Step 1, confirm GREEN).

- [ ] **Step 3: Regenerate the migration**

Run: `cd packages/db && npm run db:generate:control-plane`
Expected: a new `000X_*.sql` file appears under `packages/db/drizzle/control-plane/` adding the two
columns (drizzle-kit detects the diff against `0000_minor_nekra.sql`'s snapshot) — do not hand-edit
any generated `.sql` file.

- [ ] **Step 4: Verify packages/db tests still pass**

Run: `cd packages/db && npx vitest run`
Expected: all previously-passing tests still pass, plus the new assertion from Step 2.

- [ ] **Step 5: Write the failing document-service test**

Create `apps/control-plane/test/document-service.test.ts` — container bootstrap creating
`control_plane.projects`, `control_plane.issues`, `control_plane.documents` (with the
`document_kind` enum), `control_plane.document_revisions` (including the two new columns),
and `control_plane.users`:

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createDocument, addRevision, approveRevision } from '../server/utils/document-service.js'
// ... container bootstrap, insertTestProject/insertTestUser helpers ...

describe('createDocument', () => {
  it('creates a document with its first revision at revisionNumber 1', async () => {
    const projectId = await insertTestProject(db)

    const doc = await createDocument({ projectId, kind: 'PLAN', title: 'Initial plan', content: 'Do the thing' })

    expect(doc.kind).toBe('PLAN')
    expect(doc.latestRevision.revisionNumber).toBe(1)
    expect(doc.latestRevision.content).toBe('Do the thing')
    expect(doc.latestRevision.approvedAt).toBeNull()
  })
})

describe('addRevision', () => {
  it('appends revisionNumber 2 without altering revision 1', async () => {
    const projectId = await insertTestProject(db)
    const doc = await createDocument({ projectId, kind: 'PLAN', title: 'Plan', content: 'v1' })

    const revision2 = await addRevision(doc.id, 'v2')

    expect(revision2.revisionNumber).toBe(2)
    expect(revision2.content).toBe('v2')
  })
})

describe('approveRevision', () => {
  it('sets approvedAt/approvedByUserId and writes one outbox event', async () => {
    const projectId = await insertTestProject(db)
    const userId = await insertTestUser(db)
    const doc = await createDocument({ projectId, kind: 'QA_REPORT', title: 'QA', content: 'passed' })

    const approved = await approveRevision(doc.latestRevision.id, userId)

    expect(approved?.approvedAt).not.toBeNull()
    expect(approved?.approvedByUserId).toBe(userId)

    const rows = await db.execute(sql`select * from control_plane.outbox_events where aggregate_id = ${doc.latestRevision.id}`)
    expect(rows.rows).toHaveLength(1)
    expect(rows.rows[0].event_type).toBe('DOCUMENT_REVISION_APPROVED')
  })

  it('returns null approving an unknown revision id', async () => {
    expect(await approveRevision('00000000-0000-0000-0000-000000000000', await insertTestUser(db))).toBeNull()
  })
})
```

- [ ] **Step 6: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/document-service.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 7: Implement the service**

Create `apps/control-plane/server/utils/document-service.ts` implementing `createDocument`,
`addRevision`, `approveRevision` per the interfaces above. `createDocument` inserts a `documents` row
and a `document_revisions` row (revisionNumber 1) in one `getDb().transaction()` (no outbox — see
Global Constraints). `approveRevision` uses `withOutbox()` from Task 1, updating `approvedAt`/
`approvedByUserId` on the existing revision row (never inserting a new one) inside that transaction.

- [ ] **Step 8: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/document-service.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 9: Write the route handlers**

Create the three route files listed under Files above, following the same
`defineEventHandler`/`readBody`/Zod-validate/`createError`-on-404-or-400 pattern established in
Tasks 2-3. `POST /api/issues/[issueId]/documents` validates the issue exists first (`getIssue`);
`POST /api/document-revisions/[revisionId]/approve` accepts `{ approvedByUserId: string }` in the
body (no session/auth yet, per Global Constraints — the caller supplies the approver id explicitly
until Stage 4).

- [ ] **Step 10: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add packages/db/src/control-plane/document-revisions.ts packages/db/drizzle/control-plane packages/db/test/schema.test.ts apps/control-plane/server apps/control-plane/test/document-service.test.ts
git commit -m "feat: add Document revision and approval API"
```

---

### Task 5: Notification read, unread-count, and mark-read API

**Files:**
- Create: `apps/control-plane/server/utils/notification-service.ts`
- Create: `apps/control-plane/server/api/projects/[projectId]/notifications/index.get.ts`
- Create: `apps/control-plane/server/api/projects/[projectId]/notifications/unread-count.get.ts`
- Create: `apps/control-plane/server/api/projects/[projectId]/notifications/read.post.ts`
- Create: `apps/control-plane/test/notification-service.test.ts`

**Interfaces:**
- Consumes: `getDb()` (Task 1).
- Produces (Task 6's SSE stream reuses `listNotifications` for its initial replay batch):
  ```ts
  export interface NotificationView {
    notificationId: string
    eventKey: string
    type: string
    severity: string
    title: string
    message: string | null
    readAt: string | null
    createdAt: string
  }
  export async function listNotifications(projectId: string, opts?: { afterId?: bigint, limit?: number }): Promise<NotificationView[]>
  export async function unreadCount(projectId: string): Promise<number>
  export async function markRead(projectId: string, notificationIds: string[]): Promise<number> // returns count actually marked
  ```
- `listNotifications` orders by the internal `id` (bigserial) ascending and, when `opts.afterId` is
  given, returns only rows with `id > afterId` — this is the cursor the SSE stream's `Last-Event-ID`
  replay (Task 6) is built on. `notification_id` (uuid) is what's exposed externally; the internal
  bigint `id` is never returned in `NotificationView`, only used server-side for cursoring.
- `markRead` enforces the existing Java behavior: reject (throw, `statusCode: 400`) if
  `notificationIds.length > 100` — same limit as `NotificationCommandService.markRead` in the Java
  side (`src/main/java/com/example/worker/notification/application/service/NotificationCommandService.java`).

- [ ] **Step 1: Write the failing service test**

Create `apps/control-plane/test/notification-service.test.ts` — container bootstrap creating
`control_plane.projects` and `control_plane.notifications` matching
`packages/db/src/control-plane/notifications.ts`'s exact columns:

```ts
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { listNotifications, unreadCount, markRead } from '../server/utils/notification-service.js'
// ... container bootstrap + insertTestProject + a helper insertTestNotification(db, projectId, overrides)

describe('listNotifications / unreadCount / markRead', () => {
  it('lists notifications for a project ordered oldest first, respects afterId cursor', async () => {
    const projectId = await insertTestProject(db)
    const first = await insertTestNotification(db, projectId, { title: 'First' })
    const second = await insertTestNotification(db, projectId, { title: 'Second' })

    const all = await listNotifications(projectId)
    expect(all.map((n) => n.title)).toEqual(['First', 'Second'])

    const afterFirst = await listNotifications(projectId, { afterId: first.internalId })
    expect(afterFirst.map((n) => n.title)).toEqual(['Second'])
  })

  it('counts only unread notifications for the project', async () => {
    const projectId = await insertTestProject(db)
    await insertTestNotification(db, projectId, { readAt: null })
    await insertTestNotification(db, projectId, { readAt: new Date() })

    expect(await unreadCount(projectId)).toBe(1)
  })

  it('marks the given notification ids read and returns the count actually changed', async () => {
    const projectId = await insertTestProject(db)
    const n = await insertTestNotification(db, projectId, { readAt: null })

    const changed = await markRead(projectId, [n.notificationId])
    expect(changed).toBe(1)
    expect(await unreadCount(projectId)).toBe(0)
  })

  it('rejects marking more than 100 notification ids at once', async () => {
    const projectId = await insertTestProject(db)
    const tooMany = Array.from({ length: 101 }, () => '00000000-0000-0000-0000-000000000000')
    await expect(markRead(projectId, tooMany)).rejects.toThrow()
  })
})
```

`insertTestNotification` must return `{ notificationId, internalId }` so the test can use the raw
bigint `id` for the cursor assertion above — expose it from the test helper only, not from the
service's public `NotificationView`.

- [ ] **Step 2: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/notification-service.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the service**

Create `apps/control-plane/server/utils/notification-service.ts` per the interfaces above, using
Drizzle's `gt()`/`and()`/`eq()`/`inArray()`/`isNull()` operators against `controlPlane.notifications`.

- [ ] **Step 4: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/notification-service.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Write the route handlers**

Create the three route files. `GET .../notifications` accepts an optional `?afterId=` query param
(string, parsed to `BigInt` if present); `POST .../notifications/read` accepts `{ notificationIds:
string[] }`.

- [ ] **Step 6: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/server apps/control-plane/test/notification-service.test.ts
git commit -m "feat: add Notification read, unread-count, and mark-read API"
```

---

### Task 6: Notification SSE stream with Last-Event-ID replay

**Files:**
- Create: `apps/control-plane/server/api/projects/[projectId]/notifications/stream.get.ts`
- Create: `apps/control-plane/test/notification-stream.test.ts`

**Interfaces:**
- Consumes: `listNotifications()` (Task 5).
- The stream emits one SSE event per notification as `event: notification.created`, `id: <internal
  bigint id as decimal string>`, `data: <JSON.stringify(NotificationView)>` — the `id:` field is what
  browsers echo back as the `Last-Event-ID` request header on reconnect, per the SSE spec; this is
  exactly the mechanism the migration plan's `## 트랜잭션·알림·인증 규칙` requires ("`Last-Event-ID`
  기반 replay").
- On connect, if the request has a `Last-Event-ID` header, replay every notification with `id` greater
  than that value immediately (via `listNotifications(projectId, { afterId: BigInt(lastEventId) })`)
  before the connection goes idle. If there is no `Last-Event-ID` header, this task does not replay
  full history — it only guarantees replay-since-last-seen, matching "reset 동작": if the client's
  cursor is unrecoverable (e.g. `Last-Event-ID` parses to `NaN`/negative), emit one `event: reset`
  (no `data` payload needed beyond `{}`) instead of throwing, so the client knows to reload its full
  Inbox from `GET .../notifications` rather than trusting a broken cursor.
- This task does not implement live push for *new* notifications arriving after the initial replay
  (that requires a Postgres `LISTEN/NOTIFY` or polling loop wired to the transactional outbox
  consumer, which is Stage 6 per the migration plan's stage table — "engine event 소비"). Scope this
  task to: connect, replay-since-cursor or reset, then keep the connection open with a periodic
  H3-level keep-alive comment (`: keep-alive\n\n` every 15s) so proxies don't time it out. Document
  this scope boundary in the commit message and the analysis doc — do not silently expand scope to
  implement live push here.

- [ ] **Step 1: Write the failing test**

Create `apps/control-plane/test/notification-stream.test.ts` using `@nuxt/test-utils` (this is the
first task in this plan to exercise the actual Nitro route over HTTP rather than calling the service
function directly, since SSE framing/headers are the behavior under test, not just the data):

```ts
import { describe, expect, it } from 'vitest'
import { setup, $fetch } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'

await setup({ rootDir: fileURLToPath(new URL('..', import.meta.url)) })

describe('GET /api/projects/:projectId/notifications/stream', () => {
  it('replays notifications after Last-Event-ID and includes an id: line browsers can echo back', async () => {
    // Arrange a project + two notifications via the existing REST endpoints (index.post.ts /
    // registerProject-backed route from Task 2, then direct DB insert for notifications since
    // there is no notification-creation HTTP route in this plan - Engine publishes those via
    // Kafka in a later stage). Use the raw response body (not $fetch's JSON parsing) to inspect
    // SSE framing: fetch the URL directly and read a bounded number of bytes with a short timeout,
    // since the stream stays open (see Step 1 note on keep-alive above) - use a real HTTP client
    // (undici/`fetch`) with an AbortController timeout of ~500ms rather than $fetch, which does not
    // expose a readable stream cleanly for SSE. Assert the response Content-Type is
    // 'text/event-stream' and the first chunk contains 'event: notification.created' and an
    // 'id: ' line matching the seeded notification's internal id.
  })

  it('emits event: reset when Last-Event-ID is not a valid cursor', async () => {
    // Same HTTP-with-timeout approach, header 'Last-Event-ID': 'not-a-number', assert the first
    // chunk contains 'event: reset'.
  })
})
```

Do not leave the two test bodies as comments in the real implementation — the comments above describe
the approach; write the actual `fetch`/`AbortController`/assertion code when you implement this step.
This is flagged explicitly because SSE-over-HTTP testing is the one place in this plan where "the
approach" and "the code" might otherwise drift — they must not.

- [ ] **Step 2: Run test, verify RED**

Run: `cd apps/control-plane && npx vitest run test/notification-stream.test.ts`
Expected: FAIL — route does not exist (404).

- [ ] **Step 3: Implement the route**

Create `apps/control-plane/server/api/projects/[projectId]/notifications/stream.get.ts` using H3's
`setResponseHeaders` (`Content-Type: text/event-stream`, `Cache-Control: no-cache`, `Connection:
keep-alive`) and `sendStream`/a `ReadableStream` that: parses `Last-Event-ID` from
`getHeader(event, 'last-event-id')`; on invalid/missing cursor with no prior state, writes `event:
reset\ndata: {}\n\n` — on a valid numeric cursor, calls `listNotifications(projectId, { afterId:
BigInt(lastEventId) })` and writes one `event: notification.created\nid: <id>\ndata:
<json>\n\n` block per row; then starts a `setInterval` writing `: keep-alive\n\n` every 15000ms,
clearing it in the stream's `cancel()`/on client disconnect (`event.node.req.on('close', ...)`).

- [ ] **Step 4: Run test, verify GREEN**

Run: `cd apps/control-plane && npx vitest run test/notification-stream.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 5: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/server/api/projects/[projectId]/notifications/stream.get.ts apps/control-plane/test/notification-stream.test.ts
git commit -m "feat: add Notification SSE stream with Last-Event-ID replay"
```

---

### Task 7: End-to-end API integration test (golden path)

**Files:**
- Create: `apps/control-plane/test/api.e2e.test.ts`

**Interfaces:**
- Consumes: every route from Tasks 2-5 (not the SSE stream — that already has its own
  `@nuxt/test-utils` coverage in Task 6).

- [ ] **Step 1: Write the end-to-end test**

Create `apps/control-plane/test/api.e2e.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { setup, $fetch } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'

await setup({ rootDir: fileURLToPath(new URL('..', import.meta.url)) })

describe('Control Plane API golden path', () => {
  it('registers a project, files an issue, drafts and approves a document, and lists notifications', async () => {
    const project = await $fetch('/api/projects', {
      method: 'POST',
      body: { name: 'E2E Project', repositoryUri: 'https://github.com/acme/e2e.git' },
    })
    expect(project.name).toBe('E2E Project')
    expect(project).not.toHaveProperty('credentialRef')

    const projects = await $fetch('/api/projects')
    expect(projects.some((p: any) => p.id === project.id)).toBe(true)

    const issue = await $fetch(`/api/projects/${project.id}/issues`, {
      method: 'POST',
      body: { title: 'First ticket' },
    })
    expect(issue.issueNumber).toBe(1)
    expect(issue.status).toBe('OPEN')

    const issues = await $fetch(`/api/projects/${project.id}/issues`)
    expect(issues).toHaveLength(1)

    const updated = await $fetch(`/api/issues/${issue.id}/status`, {
      method: 'PATCH',
      body: { status: 'IN_PROGRESS' },
    })
    expect(updated.status).toBe('IN_PROGRESS')

    const document = await $fetch(`/api/issues/${issue.id}/documents`, {
      method: 'POST',
      body: { projectId: project.id, issueId: issue.id, kind: 'PLAN', title: 'Plan', content: 'v1' },
    })
    expect(document.latestRevision.revisionNumber).toBe(1)
    expect(document.latestRevision.approvedAt).toBeNull()

    const revision2 = await $fetch(`/api/documents/${document.id}/revisions`, {
      method: 'POST',
      body: { content: 'v2' },
    })
    expect(revision2.revisionNumber).toBe(2)

    // No user-registration route exists yet (Stage 4) - insert one directly for this test's
    // approver id; do not fabricate a users API route to make this step convenient.
    const approverId = await insertTestUser()
    const approved = await $fetch(`/api/document-revisions/${revision2.id}/approve`, {
      method: 'POST',
      body: { approvedByUserId: approverId },
    })
    expect(approved.approvedAt).not.toBeNull()

    const notifications = await $fetch(`/api/projects/${project.id}/notifications`)
    expect(Array.isArray(notifications)).toBe(true)

    const unread = await $fetch(`/api/projects/${project.id}/notifications/unread-count`)
    expect(typeof unread.count).toBe('number')
  })
})
```

Add an `insertTestUser()` helper in this same file using the same raw-`pg`-against-`runtimeConfig`
approach as the service tests — reuse `getDb()` from `../server/utils/db.js` directly rather than
opening a second connection, since `@nuxt/test-utils`'s `setup()` boots the app against the same
`DATABASE_URL`.

This test requires a real reachable PostgreSQL at the `DATABASE_URL` the app resolves to (not a
per-test disposable container, since `@nuxt/test-utils` boots the actual app process which reads
`nuxt.config.ts`'s `runtimeConfig.databaseUrl` at its own startup, outside this test file's control).
Before writing this test, check whether the local dev Postgres (`localhost:15432`, per Task 1's
`nuxt.config.ts` default) is reachable in the execution environment. If it is not, start a
long-lived (not per-test-disposed) `postgres:16-alpine` Docker container bound to `15432` with the
schema from Tasks 2-5 applied via `drizzle-kit migrate` (using `packages/db`'s existing
`db:migrate:control-plane` script — this is also the first real exercise of that previously-unverified
script, closing the carry-item flagged in Stage 2's analysis doc), and point `DATABASE_URL` at it for
the test run. Document whichever path you took in this task's analysis doc section.

- [ ] **Step 2: Run test, verify RED then GREEN**

Run: `cd apps/control-plane && npx vitest run test/api.e2e.test.ts`
Expected: FAILs first for whatever reason is genuinely true (missing route, unreachable DB, etc.) —
fix forward until it passes. Do not skip this test or mark it `.todo` to force a false GREEN.

- [ ] **Step 3: Write the Stage 3 analysis doc**

Create `docs/03-analysis/nuxt-stage3-control-plane-core-api.analysis.md` following the style of
`docs/03-analysis/nuxt-stage2-drizzle-schema-migration-baseline.analysis.md` — acceptance-criteria
table, what was verified against real Postgres, the `db:migrate:*` carry-item resolution from this
task, and remaining scope (auth in Stage 4, live SSE push in Stage 6, screen wiring in Stage 7).

- [ ] **Step 4: Root gates and commit**

Run from repo root: `npm run test && npm run lint && npm run typecheck`

```bash
git add apps/control-plane/test/api.e2e.test.ts docs/03-analysis/nuxt-stage3-control-plane-core-api.analysis.md
git commit -m "test: verify control plane core API end to end"
```

---

## Self-Review Notes (completed by the plan author before handoff)

- **Spec coverage:** Project CRUD (Task 2), Issue CRUD (Task 3), Document revision/approval (Task 4),
  Notification read/replay (Tasks 5-6), outbox (Task 1, exercised by Tasks 3 and 4) — all of Stage 3's
  stated scope is covered. Auth (Stage 4) and live engine-event consumption (Stage 6) are explicitly
  out of scope and called out as such in Global Constraints and Task 6.
- **Placeholder scan:** Task 6/7's SSE and e2e test bodies use prose descriptions of the HTTP
  assertions rather than fully inlined code, flagged explicitly in-line as the one place needing the
  implementer's own care — this is a deliberate, named exception (SSE streaming assertions vary too
  much by H3 runtime behavior to hand-write reliably without running them), not an oversight.
- **Type consistency:** `ProjectView`/`IssueView`/`DocumentView`/`DocumentRevisionView`/
  `NotificationView` field names are used identically across the task that defines them and every
  task that consumes them (Task 3 consumes `ProjectView` only via `getProject`'s null-check, not its
  fields; Task 4 consumes `IssueView` only via `getIssue`'s null-check; Task 6 consumes
  `NotificationView` fields directly from Task 5 — checked for match).
