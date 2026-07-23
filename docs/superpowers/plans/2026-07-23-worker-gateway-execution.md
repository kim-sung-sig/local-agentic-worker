# Worker Gateway execution plan

> Fresh `/writePlan` output. Baseline: `084a1e7` (membership visibility) and `81f212b` (`agent-worker/v1`) are committed and pushed. This plan deliberately replaces neither prior migration plans nor their implementation status.

## Goal

Deliver the remaining Worker Gateway slice: durable fake Python Agent/QA execution, standalone sticky routing, and TypeScript Temporal Activity delegation through the Gateway.

## Non-negotiable constraints

- `agent-worker/v1` is the only remote execution boundary. Do not send secrets, provider SDK objects, `WorkspaceRef`, or local/absolute paths.
- Python Worker 1 owns fake Agent/QA execution plus SQLite idempotency/event persistence.
- Gateway owns `workflowRunId -> sessionId` affinity. A failed assigned session returns retryable `UNAVAILABLE`; retain the binding and never silently reassign.
- TypeScript Worker 2 owns Temporal Workflow state, signals, and retry policy. It calls only the Gateway.
- Defer real provider runners, Git/worktree/SCM, webhooks, and SSE.
- Each task receives a fresh developer, then spec review, then quality review. Stage only task files; leave unrelated harness churn untouched.

## Task 1 — Python Worker 1: durable fake execution API

**Files:** `apps/python-agent-worker/{pyproject.toml,src/agent_worker/{app.py,ledger.py,models.py},tests/test_api.py}`.

1. Add failing API tests for valid v1 submission, duplicate idempotency-key reuse, ordered cursor reads, invalid unsafe input, and restart against the same SQLite path.
2. Implement one small HTTP host with `POST /v1/executions`, `GET /v1/executions/{id}`, `GET /v1/executions/{id}/events?after=N`, `POST /v1/executions/{id}:cancel`, and `GET /v1/capabilities`.
3. Persist executions and events in SQLite, with one execution per idempotency key and monotonic event cursor. Emit deterministic `accepted`, `running`, `completed` fake events; do not execute a provider or touch a workspace.
4. Validate the v1 shape and enforce its unsafe-field restrictions at the boundary.

**Done when:** duplicate submission after a process restart returns the original ID and no duplicate events; terminal Agent/QA results are deterministic; focused pytest is green.

**Excluded:** Gateway routing, Temporal workflow state, provider execution, workspace/SCM.

## Task 2 — standalone Gateway: sticky Python session routing

**Files:** `apps/worker-gateway/{package.json,src/{registry.ts,gateway.ts,http-server.ts},test/gateway.test.ts}` and lockfile only if dependency resolution changes it.

1. Add red tests for first assignment, same-run reuse, separate-run selection, and assigned-session failure.
2. Implement an in-process registered-session registry and a Gateway service that validates submissions with `ExecutionSubmissionSchema` before proxying them to registered Python sessions.
3. Expose `/v1/executions`, execution status/events/cancel, and capabilities. Store the `workflowRunId -> sessionId` binding on first submit.
4. If its bound session is unhealthy or unreachable, return a typed `{ code: 'UNAVAILABLE', retryable: true }`, retaining the binding. Do not add reassignment or failover.

**Done when:** repeat calls for a workflow run reach exactly its original session; a failed assigned session returns retryable `UNAVAILABLE` and later requests keep failing against that assignment; another run can receive a different session; focused test/typecheck are green.

**Excluded:** distributed registry persistence, worker migration, execution implementation, direct Temporal calls.

## Task 3 — TypeScript Worker 2: Gateway Activity adapters

**Files:** `apps/temporal-worker/src/{gateway-client.ts,activities/gateway-engine-activities.ts,worker.ts}`, `apps/temporal-worker/test/gateway-engine-activities.test.ts`.

1. Add failing adapter tests that inspect outbound submissions and assert no `workspaceRef` or local path is sent; include retryable Gateway `UNAVAILABLE` propagation.
2. Implement a Gateway client which uses only Gateway `/v1` endpoints.
3. Create activity adapters for the remote-safe Agent/QA stages and map terminal fake execution results to the existing `EngineActivities` result shapes. Build idempotency keys from the Temporal metadata, preserving Temporal retry semantics.
4. Keep workspace- and source-control-dependent activities explicitly local/deferred; do not invent a remote `WorkspaceRef` encoding. Wire the composed activity set into `createAgentWorker`.

**Done when:** the supported activities call only Gateway, receive Gateway terminal results, propagate retryable unavailability, and the workflow/signal/state implementation remains unchanged; focused test/typecheck are green.

**Excluded:** workflow state-machine changes, direct Python URL usage, SCM/Git activities.

## Task 4 — boundary verification and analysis

**Files:** focused tests from Tasks 1–3 and `docs/03-analysis/nuxt-stage6a-worker-gateway.analysis.md`.

1. Add a cross-worker smoke harness using a controlled Python Worker, Gateway session registration, and the TS Gateway client.
2. Prove duplicate/restart ledger durability, ordered events, sticky routing, `UNAVAILABLE` without reassignment, and absence of prohibited outbound fields.
3. Run `npm run test`, `npm run lint`, `npm run typecheck`, and `./gradlew.bat build`; report any environment-limited check precisely.
4. Record ownership boundaries, test evidence, fake-provider ceiling, and deferred webhook/SSE/SCM/worktree work.

**Done when:** the evidence covers every non-negotiable constraint and the analysis names all intentional deferrals.

**Excluded:** production provider integration, deployment/load tests, webhooks/SSE.

## Review checklist

- Task order is intentional: Worker API before Gateway, Gateway before Temporal adapter, then cross-boundary proof.
- No task introduces real runners, worktree ownership, SCM, webhook, or SSE behavior.
- The only failure policy is retained affinity plus retryable `UNAVAILABLE`.
