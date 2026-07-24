# Worker Gateway Local Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the committed Worker Gateway slice (Python Worker 1 → Node Gateway → Temporal Worker 2) as a runnable local integration, exercise an INTAKE→PLANNING workflow end-to-end, prove durable/ordered events with no workspace/path leakage, and decide whether to publish the committed `main` range.

**Architecture:** Add a bootable Temporal-worker runtime entrypoint that wires the existing `HttpGatewayClient` and gateway-backed activities to a Temporal server, backed by deferred-stage local activity stubs. Add a workflow driver that starts a run and drives it through the two gateway-backed READ stages (INTAKE, PLANNING) via approval signals, stopping at the PLANNING gate — never entering the deferred WORKSPACE/IMPLEMENTATION/QA/REVIEW_MERGE stages. Verify durability by inspecting the PostgreSQL ledger directly. No production/provider/SCM/webhook/SSE work is introduced.

**Tech Stack:** Node 20 ESM + TypeScript (NodeNext, `.js` import specifiers), `@temporalio/worker`/`client` `^1.20.3`, `tsx` for running TS entrypoints, Python 3.11 + FastAPI + `uv`, PostgreSQL 16, Temporal server via Docker (`temporalio/auto-setup` + `temporalio/ui`, `temporal` compose profile), `vitest` for unit tests.

## Global Constraints

- Keep `agent-worker/v1` as the only execution contract boundary. Requests must not include provider SDK objects, secrets, `WorkspaceRef`, or local/absolute paths. (verbatim from `_workspace_gateway/10_plan.md`)
- The standalone Gateway owns `workflowRunId -> Python Worker session` affinity; an unavailable assigned session returns retryable `UNAVAILABLE` and is never silently reassigned. (verbatim)
- Do not alter existing Temporal workflow semantics except to replace the relevant activity implementations with the Gateway adapters. (verbatim)
- Defer real provider runners, Git worktrees/SCM, webhooks, SSE, and any production path or workspace transfer. (verbatim)
- Do not broaden into provider, Git/worktree, SCM, webhook, or SSE work unless requested. (verbatim from handoff)
- Do not stage or discard existing unrelated harness, scratch-workspace, stackdump, or local SQLite-file changes shown by `git status`. (verbatim from handoff)
- Python Worker is PostgreSQL-only, configured via `WORKER_DATABASE_URL`, with no SQLite fallback.
- ESM import specifiers in `apps/temporal-worker` must end in `.js` (NodeNext).

---

## File Structure

New files (this plan) and their single responsibility:

- `apps/temporal-worker/src/activities/local-engine-activities.ts` — Concrete `EngineActivities` for the non-gateway methods: real no-ops for `sendNotification`/`recordAttemptHistory`, and explicit deferred throwers for `prepareWorkspace`/`manageSourceControl`. The four execution methods are placeholders the gateway wrapper overrides.
- `apps/temporal-worker/src/main.ts` — Bootable runtime entrypoint. Connects to Temporal, wires `HttpGatewayClient` + `localEngineActivities` through `createGatewayAgentWorker`, runs the worker.
- `apps/temporal-worker/src/dev/drive-intake-planning.ts` — Testable driver: starts a run, approves the INTAKE gate, polls until the run parks at the PLANNING gate.
- `apps/temporal-worker/src/dev/run-intake-planning.ts` — Thin CLI wrapper that calls the driver with a real `AgentWorkflowClient`.
- `apps/temporal-worker/test/local-engine-activities.test.ts` — Unit tests for the stubs.
- `apps/temporal-worker/test/drive-intake-planning.test.ts` — Unit tests for the driver against a fake client.
- `docker-compose.dev.yml` (repo root) — PostgreSQL 16 for the durable ledger.
- `dev/verify-ledger.sql` (repo root) — SQL assertions run via `psql` to prove durable/ordered events and non-leakage.

Modified files:

- `apps/temporal-worker/package.json` — add `tsx` devDependency and `dev`/`start:worker`/`integration:run` scripts.

Existing files this plan consumes (do not modify):

- `apps/temporal-worker/src/worker.ts` — `createGatewayAgentWorker({ gateway, project, localActivities, ...workerOptions })`.
- `apps/temporal-worker/src/gateway-client.ts` — `HttpGatewayClient(baseUrl)`.
- `apps/temporal-worker/src/client.ts` — `AgentWorkflowClient` (`start`, `approve`, `getState`).
- `packages/contracts/src/agent-engine.ts` — `EngineActivities`, `StartAgentWorkflowRequest`, `WorkflowStage`, `WorkflowRunStatus`.
- `apps/python-agent-worker/src/agent_worker/app.py` — FastAPI app `agent_worker.app:app` (auto-applies migration on connect).
- `apps/worker-gateway/src/main.ts` — reads `PYTHON_WORKER_URL`/`PYTHON_WORKER_SESSIONS`, `PORT`.

---

## Task 1: Bootable Temporal-worker runtime (stubs + entrypoint)

Deliverable: `npm run dev` (in `apps/temporal-worker`) starts a Temporal worker that connects to a Temporal server and a Gateway URL, with all eight `EngineActivities` registered.

**Files:**
- Create: `apps/temporal-worker/src/activities/local-engine-activities.ts`
- Create: `apps/temporal-worker/src/main.ts`
- Test: `apps/temporal-worker/test/local-engine-activities.test.ts`
- Modify: `apps/temporal-worker/package.json`

**Interfaces:**
- Consumes: `EngineActivities` (from `@agentic-worker/contracts`); `createGatewayAgentWorker` (from `./worker.js`); `HttpGatewayClient` (from `./gateway-client.js`).
- Produces:
  - `export const localEngineActivities: EngineActivities`
  - `apps/temporal-worker/src/main.ts` default runtime (no exports required)
  - Env contract: `GATEWAY_URL` (default `http://localhost:3001`), `TEMPORAL_ADDRESS` (default `localhost:7233`).

- [ ] **Step 1: Write the failing test**

Create `apps/temporal-worker/test/local-engine-activities.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { localEngineActivities } from '../src/activities/local-engine-activities.js'

const metadata = { workflowRunId: 'run-1', stage: 'QA' as const, attemptNumber: 1, version: 1 }

describe('localEngineActivities', () => {
  it('registers all eight EngineActivities methods', () => {
    expect(Object.keys(localEngineActivities).sort()).toEqual(
      [
        'assessTicket',
        'implement',
        'manageSourceControl',
        'planImplementation',
        'prepareWorkspace',
        'recordAttemptHistory',
        'runQualityAssurance',
        'sendNotification',
      ].sort(),
    )
  })

  it('delivers notifications as a no-op', async () => {
    await expect(
      localEngineActivities.sendNotification({
        metadata,
        ticketId: 'ticket-1',
        type: 'ACTIVITY_COMPLETED',
        severity: 'INFO',
        title: 't',
        message: 'm',
        version: 1,
      }),
    ).resolves.toEqual({ delivered: true, version: 1 })
  })

  it('records attempt history as a no-op', async () => {
    await expect(
      localEngineActivities.recordAttemptHistory({
        metadata,
        implementationArtifactRef: { value: 'a', kind: 'IMPLEMENTATION', version: 1 },
        qaReportRef: { value: 'r', kind: 'QA_REPORT', version: 1 },
        qaScore: 100,
        status: 'PASSED',
        version: 1,
      }),
    ).resolves.toEqual({ recorded: true, version: 1 })
  })

  it('rejects deferred workspace and source-control stages', async () => {
    await expect(
      localEngineActivities.prepareWorkspace({ metadata, changeType: 'FEATURE', featureSlug: 'f', version: 1 }),
    ).rejects.toThrow(/deferred/i)
    await expect(
      localEngineActivities.manageSourceControl({ metadata, workspaceRef: { value: 'w', version: 1 }, action: 'MERGE', version: 1 }),
    ).rejects.toThrow(/deferred/i)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/temporal-worker && npx vitest run test/local-engine-activities.test.ts`
Expected: FAIL — cannot resolve `../src/activities/local-engine-activities.js`.

- [ ] **Step 3: Write minimal implementation**

Create `apps/temporal-worker/src/activities/local-engine-activities.ts`:

```ts
import type { EngineActivities } from '@agentic-worker/contracts'

// The four execution methods are overridden by createGatewayEngineActivities;
// these bodies exist only to satisfy the EngineActivities shape.
export const localEngineActivities: EngineActivities = {
  assessTicket: async ({ version }) => ({ refinedSpecification: '', recommendedChangeType: 'FEATURE', version }),
  planImplementation: async ({ version }) => ({
    implementationPlanRef: { value: '', kind: 'PLAN', version },
    attemptPolicy: { minimumQaScore: 80, maxAttempts: 3, version },
    version,
  }),
  implement: async ({ version }) => ({ implementationArtifactRef: { value: '', kind: 'IMPLEMENTATION', version }, version }),
  runQualityAssurance: async ({ version }) => ({ passed: true, score: 100, reportRef: { value: '', kind: 'QA_REPORT', version }, version }),

  // Real local behaviour for stages the workflow invokes locally.
  recordAttemptHistory: async ({ version }) => ({ recorded: true, version }),
  sendNotification: async ({ version }) => ({ delivered: true, version }),

  // Deferred stages: never reached in the INTAKE→PLANNING integration path.
  prepareWorkspace: async () => {
    throw new Error('WORKSPACE stage is deferred in the local integration harness')
  },
  manageSourceControl: async () => {
    throw new Error('REVIEW_MERGE stage is deferred in the local integration harness')
  },
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/temporal-worker && npx vitest run test/local-engine-activities.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Write the runtime entrypoint**

Create `apps/temporal-worker/src/main.ts`:

```ts
import type { ProjectExecutionSnapshot } from '@agentic-worker/contracts'
import { NativeConnection } from '@temporalio/worker'

import { localEngineActivities } from './activities/local-engine-activities.js'
import { HttpGatewayClient } from './gateway-client.js'
import { createGatewayAgentWorker } from './worker.js'

const gatewayUrl = process.env.GATEWAY_URL ?? 'http://localhost:3001'
const temporalAddress = process.env.TEMPORAL_ADDRESS ?? 'localhost:7233'

// Fixed local project snapshot. Contract-safe: https git URL, no secrets, no local paths.
const project: ProjectExecutionSnapshot = {
  projectId: 'local-integration',
  repositoryUri: 'https://github.com/acme/local-integration.git',
  baseBranch: 'main',
  credentialRef: null,
  requestedSourceCommit: null,
}

async function main(): Promise<void> {
  const connection = await NativeConnection.connect({ address: temporalAddress })
  try {
    const worker = await createGatewayAgentWorker({
      connection,
      namespace: 'default',
      gateway: new HttpGatewayClient(gatewayUrl),
      project,
      localActivities: localEngineActivities,
    })
    console.log(`temporal-worker connected: temporal=${temporalAddress} gateway=${gatewayUrl}`)
    await worker.run()
  } finally {
    await connection.close()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
```

- [ ] **Step 6: Add tsx and run scripts**

Modify `apps/temporal-worker/package.json` — add to `devDependencies`: `"tsx": "^4.19.2"`, and add to `scripts`:

```json
"dev": "tsx watch src/main.ts",
"start:worker": "tsx src/main.ts",
"integration:run": "tsx src/dev/run-intake-planning.ts"
```

Run: `cd apps/temporal-worker && npm install`
Expected: `tsx` installed, lockfile updated.

- [ ] **Step 7: Typecheck**

Run: `cd apps/temporal-worker && npm run typecheck`
Expected: PASS (no errors). If `noUnusedParameters` flags the stub bodies, confirm the deferred throwers use `async () =>` with no parameters (as written above).

- [ ] **Step 8: Commit**

```bash
git add apps/temporal-worker/src/activities/local-engine-activities.ts apps/temporal-worker/src/main.ts apps/temporal-worker/test/local-engine-activities.test.ts apps/temporal-worker/package.json package-lock.json
git commit -m "feat: add bootable temporal worker runtime for local integration"
```

---

## Task 2: INTAKE→PLANNING workflow driver

Deliverable: a testable driver that starts a workflow run, approves the INTAKE gate, and returns once the run parks at the PLANNING gate, plus a CLI wrapper.

**Files:**
- Create: `apps/temporal-worker/src/dev/drive-intake-planning.ts`
- Create: `apps/temporal-worker/src/dev/run-intake-planning.ts`
- Test: `apps/temporal-worker/test/drive-intake-planning.test.ts`

**Interfaces:**
- Consumes: `AgentWorkflowClient` (from `../client.js`); `StartAgentWorkflowRequest`, `WorkflowStage`, `WorkflowRunStatus` (from `@agentic-worker/contracts`).
- Produces:
  - `export interface WorkflowDriverClient { start(request: StartAgentWorkflowRequest): Promise<unknown>; approve(workflowId: string): Promise<unknown>; getState(workflowId: string): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }> }`
  - `export interface DriveOptions { attempts?: number; delayMs?: number; sleep?: (ms: number) => Promise<void> }`
  - `export function driveIntakePlanning(client: WorkflowDriverClient, runId: string, options?: DriveOptions): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }>`
  - The driver sets `rawSpecification` to a path-like sentinel `C:\private\leak-sentinel-<runId>.md` so Task 3 can prove non-leakage.

- [ ] **Step 1: Write the failing test**

Create `apps/temporal-worker/test/drive-intake-planning.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'

import { driveIntakePlanning, type WorkflowDriverClient } from '../src/dev/drive-intake-planning.js'

function fakeClient(stages: Array<'INTAKE' | 'PLANNING'>): { client: WorkflowDriverClient; start: ReturnType<typeof vi.fn>; approve: ReturnType<typeof vi.fn> } {
  const queue = [...stages]
  const start = vi.fn(async () => undefined)
  const approve = vi.fn(async () => undefined)
  const getState = vi.fn(async () => ({ currentStage: (queue.shift() ?? 'PLANNING') as const, status: 'RUNNING' as const }))
  return { client: { start, approve, getState }, start, approve }
}

describe('driveIntakePlanning', () => {
  it('starts with a path-like sentinel spec, approves once, and returns at the PLANNING gate', async () => {
    const { client, start, approve } = fakeClient(['INTAKE', 'PLANNING'])

    const state = await driveIntakePlanning(client, 'run-1', { delayMs: 0, sleep: async () => {} })

    expect(state).toEqual({ currentStage: 'PLANNING', status: 'RUNNING' })
    expect(approve).toHaveBeenCalledTimes(1)
    expect(approve).toHaveBeenCalledWith('run-1')
    expect(start).toHaveBeenCalledWith({
      workflowRunId: 'run-1',
      ticketId: 'ticket-run-1',
      rawSpecification: 'C:\\private\\leak-sentinel-run-1.md',
    })
  })

  it('throws if the PLANNING gate is never reached', async () => {
    const start = vi.fn(async () => undefined)
    const approve = vi.fn(async () => undefined)
    const getState = vi.fn(async () => ({ currentStage: 'INTAKE' as const, status: 'RUNNING' as const }))
    const client: WorkflowDriverClient = { start, approve, getState }

    await expect(driveIntakePlanning(client, 'run-2', { attempts: 3, delayMs: 0, sleep: async () => {} })).rejects.toThrow(/PLANNING gate/i)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/temporal-worker && npx vitest run test/drive-intake-planning.test.ts`
Expected: FAIL — cannot resolve `../src/dev/drive-intake-planning.js`.

- [ ] **Step 3: Write minimal implementation**

Create `apps/temporal-worker/src/dev/drive-intake-planning.ts`:

```ts
import type { StartAgentWorkflowRequest, WorkflowRunStatus, WorkflowStage } from '@agentic-worker/contracts'

export interface WorkflowDriverClient {
  start(request: StartAgentWorkflowRequest): Promise<unknown>
  approve(workflowId: string): Promise<unknown>
  getState(workflowId: string): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }>
}

export interface DriveOptions {
  attempts?: number
  delayMs?: number
  sleep?: (ms: number) => Promise<void>
}

export async function driveIntakePlanning(
  client: WorkflowDriverClient,
  runId: string,
  options: DriveOptions = {},
): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }> {
  const attempts = options.attempts ?? 30
  const delayMs = options.delayMs ?? 1000
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))

  // Path-like sentinel proves INTAKE input never reaches the Gateway/ledger.
  const rawSpecification = `C:\\private\\leak-sentinel-${runId}.md`
  await client.start({ workflowRunId: runId, ticketId: `ticket-${runId}`, rawSpecification })

  // One approval clears the INTAKE gate; PLANNING then runs and parks at its gate.
  await client.approve(runId)

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const state = await client.getState(runId)
    if (state.currentStage === 'PLANNING' && state.status === 'RUNNING') return state
    await sleep(delayMs)
  }
  throw new Error(`workflow ${runId} did not reach the PLANNING gate`)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/temporal-worker && npx vitest run test/drive-intake-planning.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 5: Write the CLI wrapper**

Create `apps/temporal-worker/src/dev/run-intake-planning.ts`:

```ts
import { AgentWorkflowClient } from '../client.js'
import { driveIntakePlanning } from './drive-intake-planning.js'

const runId = process.argv[2] ?? `local-run-${Date.now()}`

driveIntakePlanning(new AgentWorkflowClient(), runId)
  .then((state) => {
    console.log(JSON.stringify({ runId, ...state }))
    process.exit(0)
  })
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
```

- [ ] **Step 6: Typecheck**

Run: `cd apps/temporal-worker && npm run typecheck`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/temporal-worker/src/dev/drive-intake-planning.ts apps/temporal-worker/src/dev/run-intake-planning.ts apps/temporal-worker/test/drive-intake-planning.test.ts
git commit -m "feat: add intake-planning workflow driver for local integration"
```

---

## Task 3: Ledger verification infrastructure

Deliverable: `docker-compose.dev.yml` for PostgreSQL and `dev/verify-ledger.sql` whose queries prove, for a given run, two COMPLETED executions (INTAKE, PLANNING), ordered `accepted→running→completed` events per execution, and zero occurrences of the path/workspace sentinel anywhere in the ledger.

**Files:**
- Create: `docker-compose.dev.yml` (repo root)
- Create: `dev/verify-ledger.sql` (repo root)

**Interfaces:**
- Consumes: schema `agent_worker.executions` (`idempotency_key`, `status`, `artifact_refs`) and `agent_worker.execution_events` (`execution_id`, `cursor`, `type`, `data`) from `apps/python-agent-worker/migrations/0001_agent_worker_ledger.sql`; idempotency-key format `workflowRunId:stage:attemptNumber:stageExecutionGeneration`.
- Produces: a Postgres service on `localhost:5432` (db `agent_worker`, user/pass `postgres`/`postgres`) and a psql script parametrised by `\set runid '<workflowRunId>'`.

- [ ] **Step 1: Write the PostgreSQL compose file**

Create `docker-compose.dev.yml`:

```yaml
services:
  postgres:
    image: postgres:16
    container_name: agent-worker-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: agent_worker
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d agent_worker"]
      interval: 3s
      timeout: 3s
      retries: 20
```

- [ ] **Step 2: Write the verification SQL**

Create `dev/verify-ledger.sql`:

```sql
-- Usage: psql "$WORKER_DATABASE_URL" -v runid='<workflowRunId>' -f dev/verify-ledger.sql
-- Pass the run id with -v runid='...'.

\echo '== executions for run (expect INTAKE COMPLETED and PLANNING COMPLETED) =='
SELECT split_part(idempotency_key, ':', 2) AS stage, status
FROM agent_worker.executions
WHERE idempotency_key LIKE :'runid' || ':%'
ORDER BY idempotency_key;

\echo '== ordered events per execution (expect cursors 1 accepted, 2 running, 3 completed) =='
SELECT split_part(e.idempotency_key, ':', 2) AS stage, ev.cursor, ev.type
FROM agent_worker.execution_events ev
JOIN agent_worker.executions e USING (execution_id)
WHERE e.idempotency_key LIKE :'runid' || ':%'
ORDER BY e.idempotency_key, ev.cursor;

\echo '== leakage scan (every count MUST be 0) =='
SELECT
  (SELECT count(*) FROM agent_worker.executions
     WHERE idempotency_key LIKE :'runid' || ':%'
       AND (idempotency_key LIKE '%leak-sentinel%'
            OR idempotency_key LIKE '%:\%'
            OR artifact_refs::text ILIKE '%workspaceref%'
            OR artifact_refs::text LIKE '%leak-sentinel%')) AS executions_with_leak,
  (SELECT count(*) FROM agent_worker.execution_events ev
     JOIN agent_worker.executions e USING (execution_id)
     WHERE e.idempotency_key LIKE :'runid' || ':%'
       AND (ev.data::text ILIKE '%workspaceref%'
            OR ev.data::text LIKE '%leak-sentinel%')) AS events_with_leak;
```

Note: the ledger structurally stores only the idempotency key, status, empty `artifact_refs`, and empty event `data`, so the sentinel path from Task 2 must be absent by construction — these queries are the end-to-end proof of that.

- [ ] **Step 3: Bring up PostgreSQL and confirm it is healthy**

Run:
```bash
docker compose -f docker-compose.dev.yml up -d
docker inspect --format '{{.State.Health.Status}}' agent-worker-postgres
```
Expected: prints `healthy` (retry after a few seconds if it prints `starting`).

- [ ] **Step 4: Confirm the schema query is valid against an empty ledger**

The Python Worker applies the migration on first connect, so run it once to create the schema, then verify the SQL parses. This is folded here because the SQL's validity depends on the schema existing.

Run (PowerShell, from repo root):
```powershell
cd apps/python-agent-worker
uv sync
$env:WORKER_DATABASE_URL = "postgresql://postgres:postgres@localhost:5432/agent_worker"
# Boot once to apply the migration, then stop with Ctrl+C after "Application startup complete":
uv run uvicorn agent_worker.app:app --app-dir src --port 8000
```
Then, from repo root:
```bash
psql "postgresql://postgres:postgres@localhost:5432/agent_worker" -v runid='none' -f dev/verify-ledger.sql
```
Expected: three sections print with zero data rows and `executions_with_leak=0 events_with_leak=0`. No SQL errors.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.dev.yml dev/verify-ledger.sql
git commit -m "chore: add local postgres compose and ledger verification sql"
```

---

## Task 4: End-to-end local integration run

Deliverable: the full stack (PostgreSQL → Python Worker → Gateway → Temporal Worker) running locally, one INTAKE→PLANNING run driven through it, and captured evidence that the ledger holds two durable executions with ordered events and no leakage. This is a runbook task; the "test" is the observed command output.

**Files:** none created. Uses artifacts from Tasks 1–3.

**Prerequisites:** Docker, `uv`, and Node with workspace deps installed (`npm install` at repo root). Temporal runs via Docker (`temporal` compose profile) — no separate Temporal CLI install is required. `psql` is optional on the host (Task 3/verification can run `psql` inside the `agent-worker-postgres` container).

- [ ] **Step 1: Start PostgreSQL + Temporal** (skip if already running)

Run:
```bash
docker compose -f docker-compose.dev.yml --profile temporal up -d
```
Expected: `agent-worker-postgres`, `agent-worker-temporal-postgres`, `agent-worker-temporal`, and `agent-worker-temporal-ui` all start; `agent-worker-temporal` reaches `(healthy)`. Verify:
```bash
docker ps --filter name=agent-worker --format '{{.Names}}\t{{.Status}}'
```

- [ ] **Step 2: Start the Python Worker** (terminal A, PowerShell)

Run:
```powershell
cd apps/python-agent-worker
$env:WORKER_DATABASE_URL = "postgresql://postgres:postgres@localhost:5432/agent_worker"
uv run uvicorn agent_worker.app:app --app-dir src --port 8000
```
Expected: `Application startup complete.` Verify in another shell:
```bash
curl http://localhost:8000/v1/capabilities
```
Expected: `{"workerId":"python-agent-worker","adapterIds":["fake-agent"],"modes":["READ","WRITE"]}`

- [ ] **Step 3: Start the Gateway** (terminal B, PowerShell)

Run:
```powershell
cd apps/worker-gateway
$env:PYTHON_WORKER_URL = "http://localhost:8000"
$env:PORT = "3001"
npm start
```
Expected: process stays up (builds via esbuild, then listens on 3001). Verify:
```bash
curl http://localhost:3001/v1/capabilities
```
Expected: same capabilities JSON as Step 2 (proxied through the Gateway).

- [ ] **Step 4: Confirm the Temporal server** (already running from Step 1)

Temporal is provided by the `temporal` compose profile started in Step 1 (server on `localhost:7233`, Web UI on `http://localhost:8233`). Verify:
```bash
docker exec agent-worker-temporal temporal operator namespace describe --namespace default
```
Expected: `NamespaceInfo.State  Registered` for the `default` namespace.

- [ ] **Step 5: Start the Temporal Worker** (terminal D, PowerShell)

Run:
```powershell
cd apps/temporal-worker
$env:GATEWAY_URL = "http://localhost:3001"
$env:TEMPORAL_ADDRESS = "localhost:7233"
npm run dev
```
Expected: `temporal-worker connected: temporal=localhost:7233 gateway=http://localhost:3001`

- [ ] **Step 6: Drive one INTAKE→PLANNING run** (terminal E, PowerShell)

Run:
```powershell
cd apps/temporal-worker
$env:TEMPORAL_ADDRESS = "localhost:7233"
npm run integration:run -- itest-001
```
Expected: prints `{"runId":"itest-001","currentStage":"PLANNING","status":"RUNNING"}` within ~30s. (The run intentionally parks at the PLANNING gate; it is not completed.)

- [ ] **Step 7: Verify the durable ledger**

Run (repo root):
```bash
psql "postgresql://postgres:postgres@localhost:5432/agent_worker" -v runid='itest-001' -f dev/verify-ledger.sql
```
Expected output:
- executions section: two rows — `INTAKE | COMPLETED` and `PLANNING | COMPLETED`.
- events section: six rows — for each stage, `1 accepted`, `2 running`, `3 completed`, in cursor order.
- leakage scan: `executions_with_leak=0` and `events_with_leak=0`.

- [ ] **Step 8: Verify durability across a restart**

Stop the Python Worker (Ctrl+C in terminal A), restart it (Step 2), then re-run the same driver command from Step 6 with the **same** run id is not applicable (Temporal rejects a duplicate workflow id) — instead re-query the ledger to confirm the records survived the worker restart:
```bash
psql "postgresql://postgres:postgres@localhost:5432/agent_worker" -v runid='itest-001' -f dev/verify-ledger.sql
```
Expected: identical output to Step 7 — the two executions and six events persist across the Python Worker restart (durability), and re-submission via the Gateway would return the same execution identity per idempotency key.

- [ ] **Step 9: Record the evidence**

Write the observed outputs from Steps 6–8 into `_workspace_gateway/24_local_integration_run.md` (append-only, do not modify prior `_workspace_gateway` artifacts). Include: the driver output line, the three SQL sections, and a one-line confirmation of restart durability. Then run the superpowers:verification-before-completion checklist before declaring this task done.

```bash
git add _workspace_gateway/24_local_integration_run.md
git commit -m "docs: record local worker-gateway integration run"
```

- [ ] **Step 10: Tear down**

Run:
```bash
docker compose -f docker-compose.dev.yml --profile temporal down
```
Stop the Python Worker, Gateway, and Temporal Worker (Ctrl+C in their terminals).

---

## Task 5: Publish decision for the committed range

Deliverable: an explicit, user-approved decision on whether to push the completed `main` range to `origin`. This task is **independent** of Tasks 1–4 (those commits are already reviewed/approved) and may be done first or last; recommend confirming with the user after the integration run in Task 4 gives confidence.

**Files:** none. Git only.

**Constraint reminder:** Do not stage or discard unrelated harness/scratch/stackdump/SQLite changes. `git push` publishes only already-made commits and stages nothing, so the uncommitted working-tree churn stays local — do **not** `git add -A`.

- [ ] **Step 1: Inspect exactly what would be published**

Run:
```bash
git log --oneline origin/main..main
```
Expected: the range from the handoff — 19 commits beginning at `9bcd9cf` ("feat: add durable Python agent worker host") and ending at `2abc951` ("fix: reject Windows root paths at worker boundary"), plus the commits added by Tasks 1–4 if those ran first.

- [ ] **Step 2: Confirm no unintended staged changes**

Run:
```bash
git status --short
git diff --cached --stat
```
Expected: `git diff --cached --stat` is empty (nothing staged). Working-tree churn (harness, `_workspace*`, `grep.exe.stackdump`, `*.sqlite3`) remains unstaged and untouched.

- [ ] **Step 3: Get explicit user approval to push**

Pushing to `origin/main` is an outward-facing, hard-to-reverse action. Present the `git log --oneline origin/main..main` output to the user and ask for a clear yes before pushing. Do not push without it.

- [ ] **Step 4: Push (only after approval)**

Run:
```bash
git push origin main
```
Expected: `origin/main` fast-forwards to local `main`; `git log --oneline origin/main..main` is then empty.

- [ ] **Step 5: Confirm**

Run:
```bash
git status -sb
```
Expected: `## main...origin/main` with no `ahead` count.

---

## Self-Review

**1. Spec coverage (handoff "Next recommended work"):**
- Item 1 "Decide whether to publish the committed range" → Task 5. ✅
- Item 2 "Run the Worker stack in a real local integration configuration (PostgreSQL, Python Worker with `WORKER_DATABASE_URL`, Gateway with registered `PYTHON_WORKER_URL`/sessions, then Temporal Worker)" → Task 1 (bootable Temporal worker, previously missing), Task 3 (PostgreSQL), Task 4 Steps 1–5. ✅
- Item 3 "Exercise an INTAKE/PLANNING workflow submission through Temporal→Gateway→Python; confirm durable execution, ordered events, and no workspace/path leakage" → Task 2 (driver), Task 4 Steps 6–8, Task 3 verification SQL. ✅
- Deferred scope (provider/worktree/SCM/webhook/SSE) is honored: local stubs throw for WORKSPACE/REVIEW_MERGE and the driver stops at the PLANNING gate. ✅
- Handoff verification note (Control Plane DB-startup race → use systematic-debugging if it reappears): not a task; surfaced as a risk in the handoff. If it reappears during Task 4, invoke superpowers:systematic-debugging rather than adding a blind retry.

**2. Placeholder scan:** No "TBD"/"handle edge cases"/"similar to Task N"/"write tests for the above" — every code and command step contains concrete content. ✅

**3. Type consistency:** `WorkflowDriverClient.getState` returns `{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }`, matching `AgentWorkflowClient.getState` in `apps/temporal-worker/src/client.ts`. `driveIntakePlanning` signature is identical in the Interfaces block, the test, and the implementation. `localEngineActivities` is typed `EngineActivities` and its eight method names match `packages/contracts/src/agent-engine.ts`. Env var names (`GATEWAY_URL`, `TEMPORAL_ADDRESS`, `WORKER_DATABASE_URL`, `PYTHON_WORKER_URL`, `PORT`) match `main.ts` and the existing `worker-gateway`/`python-agent-worker` sources. Idempotency-key parsing in `dev/verify-ledger.sql` (`split_part(..., ':', 2)` → stage) matches the format produced in `gateway-engine-activities.ts`. ✅
