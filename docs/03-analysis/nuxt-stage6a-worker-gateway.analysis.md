# Stage 6A Worker Gateway execution slice

## Ownership and boundary

| Boundary | Owner | Evidence |
|---|---|---|
| `agent-worker/v1` validation and durable fake execution | Python Worker 1 | `apps/python-agent-worker/tests/test_api.py`: PostgreSQL migration, duplicate/restart, event ordering, and contract matrix |
| `workflowRunId -> sessionId` affinity | Worker Gateway | `apps/worker-gateway/test/gateway.test.ts`: affinity and assigned-session failure cases |
| workflow state, retries, and safe remote Activity adaptation | Temporal Worker 2 | `apps/temporal-worker/test/gateway-engine-activities.test.ts` |

## Verification evidence

- `uv run pytest tests/test_api.py -q` in `apps/python-agent-worker`: **28 passed** with Docker available. It proves the `agent_worker.executions` unique idempotency row and `agent_worker.execution_events` cursors survive an app restart and return `accepted`, `running`, `completed` in order. The Worker owns only the PostgreSQL `agent_worker` schema and those two tables.
- `npm run test --workspace=@agentic-worker/worker-gateway`: **12 passed**. Bound unhealthy/unreachable sessions return retryable `UNAVAILABLE`; the binding is retained and the alternate session is not called.
- `npm run test --workspace=@agentic-worker/temporal-worker -- --run test/gateway-engine-activities.test.ts`: **10 passed**. The Task 4 smoke starts the real Gateway HTTP server, reaches it through `HttpGatewayClient`, records the controlled Worker session's submission, verifies ordered events, then marks that assigned session unavailable. The TypeScript client receives `GatewayUnavailableError`, and no other session receives the request.
- The same smoke submits an activity whose source specification contains a Windows path and asserts serialized outbound data contains neither `workspaceRef`, an absolute path, nor `file:`. Existing contract and Gateway tests also reject secret-like keys and unsafe Worker response values.
- Root `npm run lint`, root `npm run typecheck`, and `./gradlew.bat build` passed. Root `npm run test` hit the 120-second executor limit after these observed passes: control-plane 52, Temporal 25, Gateway 12, contracts 10, and db 23; its remaining workspace result was not observed, so it is not reported as a pass.

## Intentional ceiling and deferrals

The Worker returns deterministic completed fake Agent/QA results; no provider credentials, provider SDK, workspace, Git/worktree, SCM, webhook, SSE, load test, or deployment behavior is present. The controlled HTTP smoke uses a registered fake Worker session. PostgreSQL durability is proven independently by the Docker-backed Python integration suite, not by a single process-spanning Python-process → Node Gateway → Temporal deployment test. That deployment-level composition remains a follow-up when runtime orchestration is in scope.
