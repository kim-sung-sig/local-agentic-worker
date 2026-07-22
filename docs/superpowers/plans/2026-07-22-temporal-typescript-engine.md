# Temporal TypeScript Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the Java Temporal agent workflow and its activity contract to the TypeScript worker, then expose a small Temporal Client command API.

**Architecture:** `packages/contracts` owns JSON-safe command, stage, status, request, and activity types. `apps/temporal-worker` contains deterministic Temporal workflow code, injected activity implementations, worker creation, and a client wrapper; the workflow follows the Java stage and gate semantics without accessing the Control Plane database. Tests use `@temporalio/testing` to exercise signals, queries, retry policy, and worker restart/replay.

**Tech Stack:** TypeScript, `@temporalio/workflow`, `@temporalio/worker`, `@temporalio/client`, `@temporalio/testing`, Vitest.

## Global Constraints

- Preserve the Java workflow stage order: `INTAKE → PLANNING → WORKSPACE → IMPLEMENTATION → QA → REVIEW_MERGE`.
- Preserve Java run statuses: `RUNNING | PAUSED | COMPLETED | FAILED | CANCELLED` and reject-only-to-current-or-earlier-stage rule.
- Activities may call external systems; workflow code must remain deterministic and must not access the Control Plane database.
- The TypeScript worker must use a distinct migration task queue; do not consume the Java production queue during parallel verification.
- Do not modify `src/main/java`, `frontend/`, `docker/`, `compose.yml`, or existing Control Plane routes.
- TDD: create a failing Vitest test before implementation for each task. Keep one task per commit and leave unrelated harness churn unstaged.
- Root gates must pass: `npm run test`, `npm run lint`, `npm run typecheck`, and `./gradlew build` in PowerShell.

---

## File Structure

```
packages/contracts/src/agent-engine.ts                         -- shared DTOs, commands, states
packages/contracts/src/index.ts                                -- exports engine contract
packages/contracts/test/agent-engine.test.ts                   -- contract invariants
apps/temporal-worker/src/workflows/agent-worker-workflow.ts    -- deterministic workflow and signals/queries
apps/temporal-worker/src/activities/engine-activities.ts       -- activity interface and injectable implementations
apps/temporal-worker/src/worker.ts                             -- Worker factory, migration queue
apps/temporal-worker/src/client.ts                             -- start/signal/query command wrapper
apps/temporal-worker/test/agent-worker-workflow.test.ts        -- Temporal time-skipping integration tests
apps/temporal-worker/test/client.test.ts                       -- client command mapping tests
docs/03-analysis/nuxt-stage5-temporal-typescript-engine.analysis.md -- verified coverage and known gaps
```

### Task 1: Shared Agent Engine contract

**Files:**
- Create: `packages/contracts/src/agent-engine.ts`
- Modify: `packages/contracts/src/index.ts`
- Create: `packages/contracts/test/agent-engine.test.ts`

**Interfaces:**
- Produces `WorkflowStage`, `WorkflowRunStatus`, `StartAgentWorkflowRequest`, `ActivityRequestMetadata`, `AttemptPolicy`, `AgentWorkflowCommand`, and `EngineActivities` TypeScript types.
- `StartAgentWorkflowRequest` contains `{ workflowRunId: string; ticketId: string; rawSpecification: string }`.
- `AttemptPolicy` contains `{ minimumQaScore: number; maxAttempts: number }`.

- [ ] **Step 1: Write the failing contract test**

```ts
import { expect, it } from 'vitest'
import { WORKFLOW_STAGES, WORKFLOW_RUN_STATUSES } from '../src/agent-engine.js'

it('keeps the Java workflow stage order and terminal statuses', () => {
  expect(WORKFLOW_STAGES).toEqual(['INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA', 'REVIEW_MERGE'])
  expect(WORKFLOW_RUN_STATUSES).toContain('COMPLETED')
  expect(WORKFLOW_RUN_STATUSES).toContain('CANCELLED')
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/contracts -- agent-engine.test.ts`
Expected: FAIL because the module and exports do not exist.

- [ ] **Step 3: Implement the minimal contract**

```ts
export const WORKFLOW_STAGES = ['INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA', 'REVIEW_MERGE'] as const
export type WorkflowStage = (typeof WORKFLOW_STAGES)[number]
export const WORKFLOW_RUN_STATUSES = ['RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED'] as const
export type WorkflowRunStatus = (typeof WORKFLOW_RUN_STATUSES)[number]
```

Add only JSON-serializable request/result interfaces needed by the workflow and activity calls; export them from `src/index.ts`.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/contracts && npm run typecheck --workspace @agentic-worker/contracts`

```bash
git add packages/contracts/src packages/contracts/test/agent-engine.test.ts
git commit -m "feat: add Temporal agent engine contracts"
```

### Task 2: Deterministic Agent Worker workflow

**Files:**
- Create: `apps/temporal-worker/src/workflows/agent-worker-workflow.ts`
- Create: `apps/temporal-worker/src/activities/engine-activities.ts`
- Create: `apps/temporal-worker/test/agent-worker-workflow.test.ts`

**Interfaces:**
- Consumes Task 1 contract types.
- Produces workflow exports `run`, `approve`, `reject(reason, targetStage)`, `requestRevision(reason)`, `retryStage`, `cancel`, `currentStage`, and `status`.
- Activities are named `assessTicket`, `planImplementation`, `prepareWorkspace`, `implement`, `runQualityAssurance`, `recordAttemptHistory`, `manageSourceControl`, and `sendNotification`.

- [ ] **Step 1: Write failing Temporal tests**

Create tests using `TestWorkflowEnvironment.createTimeSkipping()` that start the workflow with deterministic fake activities and assert: (a) each stage blocks until `approve`, (b) QA below `minimumQaScore` retries only until `maxAttempts` then returns `FAILED`, (c) `reject('reason', 'PLANNING')` pauses and `retryStage` re-enters PLANNING, (d) a forward rejection is ignored, and (e) `cancel` yields `CANCELLED`.

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/temporal-worker -- agent-worker-workflow.test.ts`
Expected: FAIL because workflow and activity modules do not exist.

- [ ] **Step 3: Implement minimal replay-safe workflow**

Use only `@temporalio/workflow` APIs (`proxyActivities`, `defineSignal`, `defineQuery`, `setHandler`, `condition`). Configure the activity proxy with `startToCloseTimeout: '10 minutes'` and a bounded retry policy. Keep signal state in workflow-local variables, use `workflow.now` for history timestamps, and do not import Node APIs, database code, or activity implementation code into the workflow module.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/temporal-worker && npm run typecheck --workspace @agentic-worker/temporal-worker`

```bash
git add apps/temporal-worker/src/workflows apps/temporal-worker/src/activities apps/temporal-worker/test/agent-worker-workflow.test.ts apps/temporal-worker/package.json package-lock.json
git commit -m "feat: port agent workflow to Temporal TypeScript"
```

### Task 3: Worker process and Client command API

**Files:**
- Create: `apps/temporal-worker/src/worker.ts`
- Create: `apps/temporal-worker/src/client.ts`
- Create: `apps/temporal-worker/test/client.test.ts`

**Interfaces:**
- `createAgentWorker(options)` returns an unstarted `Worker` with workflow path, activities, and `TASK_QUEUE = 'agent-worker-engine-typescript'`.
- `AgentWorkflowClient` exposes `start(request)`, `approve(workflowId)`, `reject(workflowId, reason, targetStage)`, `requestRevision(workflowId, reason)`, `retryStage(workflowId)`, `cancel(workflowId)`, and `getState(workflowId)`.

- [ ] **Step 1: Write failing client tests**

Mock the Temporal Client and assert `start` uses `workflowId: request.workflowRunId`, the TypeScript migration queue, and `run`; assert each signal method maps exactly once to its workflow signal; assert `getState` queries both `currentStage` and `status`.

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/temporal-worker -- client.test.ts`
Expected: FAIL because the Worker factory and client wrapper do not exist.

- [ ] **Step 3: Implement worker and client**

Build a Worker factory that receives activities explicitly and points `workflowsPath` at Task 2. Build the thin wrapper around `@temporalio/client`; do not add HTTP routes in this stage. Update `worker-info.ts` so its task queue is the TypeScript migration queue.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/temporal-worker && npm run typecheck --workspace @agentic-worker/temporal-worker`

```bash
git add apps/temporal-worker/src apps/temporal-worker/test/client.test.ts apps/temporal-worker/test/smoke.test.ts apps/temporal-worker/package.json package-lock.json
git commit -m "feat: add Temporal worker and agent workflow client"
```

### Task 4: Stage validation and analysis

**Files:**
- Create: `docs/03-analysis/nuxt-stage5-temporal-typescript-engine.analysis.md`

**Interfaces:**
- Consumes all previous tasks and records verified parity only; does not claim activity adapters or Worker Gateway implementation, which are Stage 6 work.

- [ ] **Step 1: Run complete verification**

Run in PowerShell from repository root: `npm run test`, `npm run lint`, `npm run typecheck`, then `./gradlew build`.
Expected: all commands exit 0. If a pre-existing container startup flake occurs, record the first failure and clean re-run result.

- [ ] **Step 2: Write analysis**

Document a table covering stage/signal/query, QA retry exhaustion, rejection/retry, cancellation, worker queue separation, and explicit remaining Stage 6 dependencies (real activity adapters, Gateway affinity, webhook/event/SSE connection).

- [ ] **Step 3: Commit**

```bash
git add docs/03-analysis/nuxt-stage5-temporal-typescript-engine.analysis.md
git commit -m "docs: analyze Temporal TypeScript engine migration"
```

## Self-Review Notes

- **Spec coverage:** Task 1 ports contracts; Task 2 ports stage, signal, retry, and restart-safe workflow behavior; Task 3 provides separate Worker and Client commands; Task 4 runs the required root gates and documents Stage 6 exclusions.
- **Intentional exclusions:** real Codex/Python activity adapters, sticky Worker Gateway, webhooks, and live notification delivery remain Stage 6; no frontend or Java deletion is included.
- **Type consistency:** workflow request, stage/status unions, and command methods are all defined by Task 1 and consumed unchanged by Tasks 2–3.
