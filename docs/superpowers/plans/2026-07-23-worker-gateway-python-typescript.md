# Worker Gateway, Python Worker 1, and TypeScript Worker 2 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Control Plane project visibility and introduce a worker-first execution slice in which Python Worker 1 owns agent/QA execution and TypeScript Worker 2 owns Temporal workflow state, signals, retries, and Gateway activity delegation.

**Architecture:** A standalone Worker Gateway owns registered Python-worker health and the `workflowRunId → sessionId` sticky mapping. The Python host exposes the versioned HTTP contract and persists idempotent execution/event state in SQLite. The Temporal Worker calls only a Gateway client; it never calls a Python URL or forwards a local `WorkspaceRef`.

**Tech Stack:** Nuxt/Nitro, Drizzle, TypeScript, Zod, Node HTTP, Temporal TypeScript SDK, Python 3.11, FastAPI, Uvicorn, Pydantic, SQLite, pytest.

## Global Constraints

- Project lists return only projects where the session user has a membership; `403` remains an access-denied result for a direct unauthorized project route.
- Python Worker 1 owns Agent/QA execution state. TypeScript Worker 2 owns workflow stage/status, signals, and retries.
- Gateway is a standalone service, not embedded in a Temporal workflow or Python Worker.
- Gateway maps a `workflowRunId` to one healthy Python worker session. When that session is unavailable, return retryable `UNAVAILABLE`; never silently reassign.
- `agent-worker/v1` JSON must exclude secrets, provider SDK objects, local absolute paths, and `WorkspaceRef` values.
- Python persistence is SQLite at `WORKER_STATE_PATH`; duplicate submission with the same idempotency key returns the same execution ID and does not run again.
- Only fake provider execution is in scope. Real Codex/Claude runners, Git clone/worktree, SCM, webhook, event consumption, and live SSE remain later slices.
- Do not modify Java, legacy frontend, Docker/compose, or existing Control Plane UI behavior beyond resolving the 403 root cause.
- TDD, one task per commit, no staging unrelated harness churn.

---

## File Structure

```
apps/control-plane/server/utils/project-service.ts
apps/control-plane/server/api/projects/index.get.ts
apps/control-plane/test/project-service.test.ts
packages/contracts/src/agent-worker-v1.ts
packages/contracts/src/index.ts
packages/contracts/test/agent-worker-v1.test.ts
packages/contracts/test/fixtures/agent-worker-v1/*.json
apps/worker-gateway/{package.json,src/gateway.ts,src/registry.ts,src/http-server.ts,test/gateway.test.ts}
apps/python-agent-worker/{pyproject.toml,src/agent_worker/{app.py,ledger.py,models.py},tests/test_api.py}
apps/temporal-worker/src/{gateway-client.ts,activities/gateway-engine-activities.ts,run-worker.ts}
apps/temporal-worker/test/gateway-engine-activities.test.ts
docs/03-analysis/nuxt-stage6a-worker-gateway.analysis.md
```

### Task 1: Scope project lists by membership

**Files:** `apps/control-plane/server/utils/project-service.ts`, `apps/control-plane/server/api/projects/index.get.ts`, `apps/control-plane/test/project-service.test.ts`

**Interfaces:** Change `listProjects(): Promise<ProjectView[]>` to `listProjects(userId: string): Promise<ProjectView[]>`. The handler obtains `const user = await requireSession(event)` and calls `listProjects(user.id)`.

- [ ] **Step 1: Write the failing integration test**

```ts
it('lists only projects the caller belongs to', async () => {
  const visible = await listProjects(memberUserId)
  expect(visible.map((project) => project.id)).toEqual([memberProjectId])
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/control-plane -- project-service.test.ts`
Expected: the other user's project is returned.

- [ ] **Step 3: Implement the membership join**

Use an inner join from `controlPlane.projects` to `controlPlane.memberships`, filtered with `eq(memberships.userId, userId)`, then map only project rows through `toView`.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/control-plane -- project-service.test.ts && npm run typecheck --workspace @agentic-worker/control-plane`

```bash
git add apps/control-plane/server/utils/project-service.ts apps/control-plane/server/api/projects/index.get.ts apps/control-plane/test/project-service.test.ts
git commit -m "fix: scope project lists to memberships"
```

### Task 2: Versioned agent-worker HTTP contract

**Files:** `packages/contracts/src/agent-worker-v1.ts`, `packages/contracts/src/index.ts`, `packages/contracts/test/agent-worker-v1.test.ts`, `packages/contracts/test/fixtures/agent-worker-v1/*.json`

**Interfaces:** Export Zod schemas and inferred types for `ProjectExecutionSnapshot`, `ExecutionSubmission`, `ExecutionStatus`, `ExecutionEvent`, and `WorkerCapabilities`.

- `ProjectExecutionSnapshot` fields are exactly `projectId`, `repositoryUri`, `baseBranch`, `credentialRef`, and `requestedSourceCommit`.
- `ExecutionSubmission` includes `contractVersion: 'agent-worker/v1'`, `idempotencyKey`, `workflowRunId`, `stage`, `attemptNumber`, `stageExecutionGeneration`, `adapterId`, `project`, and `mode: 'READ' | 'WRITE'`.
- Valid idempotency key is `${workflowRunId}:${stage}:${attemptNumber}:${stageExecutionGeneration}`.

- [ ] **Step 1: Write failing schema tests**

```ts
expect(ExecutionSubmissionSchema.safeParse({
  contractVersion: 'agent-worker/v1', idempotencyKey: 'run-1:QA:2:1', workflowRunId: 'run-1', stage: 'QA', attemptNumber: 2, stageExecutionGeneration: 1,
  adapterId: 'codex-cli-python', mode: 'READ', project: projectSnapshot,
}).success).toBe(true)
expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, workspaceRef: 'C:\\secret' }).success).toBe(false)
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/contracts -- agent-worker-v1.test.ts`
Expected: module absent.

- [ ] **Step 3: Implement schemas and fixtures**

Use Zod `.strict()` objects and refinements that reject local absolute paths (`/`, `C:\\`) and secret-like keys (`token`, `password`, `secret`, `apiKey`) anywhere in submission input. Add JSON fixtures for valid submission, terminal execution, ordered events, and capabilities.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/contracts && npm run typecheck --workspace @agentic-worker/contracts`

```bash
git add packages/contracts/src packages/contracts/test
git commit -m "feat: add agent worker v1 contracts"
```

### Task 3: Standalone Gateway with sticky session routing

**Files:** `apps/worker-gateway/package.json`, `apps/worker-gateway/src/registry.ts`, `apps/worker-gateway/src/gateway.ts`, `apps/worker-gateway/src/http-server.ts`, `apps/worker-gateway/test/gateway.test.ts`

**Interfaces:**
- `WorkerRegistry.register({ workerId, sessionId, baseUrl, capabilities })`
- `Gateway.submit(submission): Promise<{ executionId: string }>`
- `Gateway.getExecution(executionId)`, `Gateway.cancel(executionId)`, and `Gateway.capabilities()`.

- [ ] **Step 1: Write failing routing tests**

```ts
it('routes repeated workflow submissions to the originally assigned session', async () => {
  await gateway.submit(submission('run-1'))
  await gateway.submit(submission('run-1', 'QA'))
  expect(client.calls.map((call) => call.sessionId)).toEqual(['python-a', 'python-a'])
})

it('returns retryable UNAVAILABLE when an assigned session is unhealthy', async () => {
  await gateway.submit(submission('run-1'))
  registry.markUnhealthy('python-a')
  await expect(gateway.submit(submission('run-1', 'QA'))).rejects.toMatchObject({ code: 'UNAVAILABLE', retryable: true })
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/worker-gateway -- gateway.test.ts`
Expected: workspace absent.

- [ ] **Step 3: Implement Gateway**

Use a small in-memory registry and mapping only for this process; expose `POST /v1/executions`, execution reads/events/cancel, and `/v1/capabilities` with Node HTTP. Proxy only the validated v1 schema to a registered Python worker. Do not implement reassignment.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/worker-gateway && npm run typecheck --workspace @agentic-worker/worker-gateway`

```bash
git add apps/worker-gateway package-lock.json
git commit -m "feat: add sticky Python worker gateway"
```

### Task 4: Python Worker 1 durable fake execution host

**Files:** `apps/python-agent-worker/pyproject.toml`, `apps/python-agent-worker/src/agent_worker/models.py`, `ledger.py`, `app.py`, `tests/test_api.py`

**Interfaces:**
- `POST /v1/executions` validates Task 2 JSON and inserts/reuses SQLite execution by `idempotencyKey`.
- `GET /v1/executions/{executionId}`, `GET /v1/executions/{executionId}/events?after=N`, `POST /v1/executions/{executionId}:cancel`, and `GET /v1/capabilities` match Task 2 response schemas.
- Fake runner emits sequence `accepted`, `running`, `completed`, returns normalized artifact refs, and never executes Codex/Claude.

- [ ] **Step 1: Write failing pytest tests**

```python
def test_duplicate_submission_reuses_execution_and_events(tmp_path):
    first = submit(client, payload("run-1:QA:1:1"))
    second = submit(client, payload("run-1:QA:1:1"))
    assert first["executionId"] == second["executionId"]
    assert events(client, first["executionId"])[0]["cursor"] == 1
```

- [ ] **Step 2: Run RED**

Run: `cd apps/python-agent-worker && uv run pytest`
Expected: package absent.

- [ ] **Step 3: Implement host**

Use only SQLite tables for executions and events. Take the database path from `WORKER_STATE_PATH`, defaulting to `worker-state.sqlite3` beside the app. Serialize only JSON-safe contract values. Enforce monotonic event cursors and normalized terminal output.

- [ ] **Step 4: Run GREEN and commit**

Run: `cd apps/python-agent-worker && uv run pytest`

```bash
git add apps/python-agent-worker
git commit -m "feat: add durable Python agent worker host"
```

### Task 5: TypeScript Worker 2 Gateway activities

**Files:** `apps/temporal-worker/src/gateway-client.ts`, `apps/temporal-worker/src/activities/gateway-engine-activities.ts`, `apps/temporal-worker/src/run-worker.ts`, `apps/temporal-worker/test/gateway-engine-activities.test.ts`

**Interfaces:**
- `GatewayClient.submit`, `getExecution`, `cancel`, and `capabilities` use only `/v1` Gateway URLs.
- `createGatewayEngineActivities(client)` creates the Agent/QA activity adapters; it receives opaque execution IDs and never accepts Python base URLs or `WorkspaceRef` values.
- `run-worker.ts` constructs `createAgentWorker({ activities })` using Gateway activities and the existing local test-only implementations only where this slice has no remote equivalent.

- [ ] **Step 1: Write failing adapter tests**

```ts
it('submits QA through Gateway without forwarding a local workspace reference', async () => {
  await activities.runQualityAssurance(qaRequest)
  expect(gateway.submit).toHaveBeenCalledWith(expect.not.objectContaining({ workspaceRef: expect.anything() }))
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/temporal-worker -- gateway-engine-activities.test.ts`
Expected: adapter module absent.

- [ ] **Step 3: Implement adapters**

Map remote-safe assessment/planning/implementation/QA submissions to Task 2 contracts. Return retryable errors unchanged when Gateway reports `UNAVAILABLE`. Leave workspace/source-control local adapter injection explicit; do not pretend to remotely execute a local workspace path.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/temporal-worker && npm run typecheck --workspace @agentic-worker/temporal-worker`

```bash
git add apps/temporal-worker/src apps/temporal-worker/test/gateway-engine-activities.test.ts
git commit -m "feat: delegate Temporal activities through worker gateway"
```

### Task 6: Integration verification and analysis

**Files:** `docs/03-analysis/nuxt-stage6a-worker-gateway.analysis.md`

- [ ] **Step 1: Run cross-worker smoke**

Start Python Worker with a temporary `WORKER_STATE_PATH`, register it in the Gateway test harness, submit duplicate executions via Gateway, restart the Python app with the same SQLite path, and assert the same execution ID and ordered events are returned.

- [ ] **Step 2: Run root gates**

Run: `npm run test`, `npm run lint`, `npm run typecheck`, and PowerShell `./gradlew.bat build`.

- [ ] **Step 3: Write analysis and commit**

Document the fixed 403 path, worker ownership boundary, sticky unavailable policy, fake-provider limitation, and deferred webhook/SSE/SCM/worktree slices.

```bash
git add docs/03-analysis/nuxt-stage6a-worker-gateway.analysis.md
git commit -m "docs: analyze worker gateway execution slice"
```

## Self-Review Notes

- **Spec coverage:** Task 1 fixes the observed 403; Tasks 2–4 establish a shared HTTP contract, Gateway, and durable Python Worker 1; Task 5 makes TypeScript Worker 2 delegate through the Gateway; Task 6 verifies the boundary.
- **Deliberate deferrals:** no provider credentials, real agent runner, Git worktree, source-control, webhook, or live SSE is implied by this slice.
- **Failure policy:** session reassignment is deliberately absent; `UNAVAILABLE` preserves session affinity and lets Temporal retry.
