# Stage 5 Analysis — Temporal TypeScript Engine

## Scope verified

Stage 5 adds a TypeScript Temporal workflow contract, deterministic workflow, injected Worker factory, and thin Client command API. It does not replace the Java engine or connect the new worker to the Control Plane.

## Verified Java parity

| Concern | Verified TypeScript behavior | Evidence |
| --- | --- | --- |
| Stages and run statuses | Preserves `INTAKE → PLANNING → WORKSPACE → IMPLEMENTATION → QA → REVIEW_MERGE` and `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`. | `packages/contracts/src/agent-engine.ts`; contract test included in root test run. |
| Activity DTO boundary | Uses Java-compatible versioned metadata and references: `ActivityRequestMetadata`, `ArtifactRef`, and `WorkspaceRef` carry their contract versions through every activity request/result. | Contract and Temporal workflow typechecks plus focused workflow tests. |
| Approval gates | Waits at `INTAKE`, `PLANNING`, `QA`, and `REVIEW_MERGE`; `WORKSPACE` and `IMPLEMENTATION` advance automatically, matching Java rather than the plan's conflicting “each stage” wording. | Temporal integration test verifies activity order and four approvals. |
| Signals and queries | Exposes `approve`, `reject`, `requestRevision`, `retryStage`, `cancel`, `currentStage`, and `status`. The Client maps every command to one signal and reads both queries for state. | `agent-worker-workflow.ts`, `client.ts`, `client.test.ts`. |
| QA retry exhaustion | A score below the planned threshold returns to `IMPLEMENTATION` until `maxAttempts`, then returns `FAILED`; attempt history is recorded for each QA result. | Temporal integration test verifies two implementation/QA calls at two allowed attempts. |
| Rejection and retry | Current/earlier-stage rejection pauses the run; `retryStage` resumes the target. Forward rejection is ignored. A QA rejection that re-enters implementation increments the attempt number. | Temporal integration tests. |
| Cancellation and replay | Cancellation at a gate returns `CANCELLED`; completed workflow history replays with `Worker.runReplayHistory`. | Temporal integration tests. |
| Queue separation | Worker and Client share `agent-worker-engine-typescript`, distinct from the Java production queue. Worker creation receives activities and returns an unstarted Worker. | `worker-info.ts`, `worker.ts`, `client.ts`, worker/client/smoke tests. |

## Root verification

All commands ran from the repository root in PowerShell on 2026-07-22.

| Run | Command | Outcome |
| --- | --- | --- |
| Initial | `npm run test` | PASS — 83 tests: Control Plane 39, Temporal Worker 15, Contracts 6, DB 23. Exit 0 after 123.7s. Node emitted three `DEP0155` deprecation warnings only. |
| Initial | `npm run lint` | FAIL — exit 1: `apps/temporal-worker/src/workflows/agent-worker-workflow.ts:55:28`, unused `gate` parameter (`@typescript-eslint/no-unused-vars`). |
| Initial | `npm run typecheck` | PASS — exit 0; all four workspaces completed. |
| Initial | `./gradlew build` | PASS — exit 0; `BUILD SUCCESSFUL`, 23 tasks up-to-date. |
| Clean rerun | `npm run test` | PASS — 83 tests with the same workspace counts. Exit 0 after 115.1s; the same non-failing `DEP0155` warnings were emitted. |
| Clean rerun | `npm run lint` | PASS — exit 0 after the scoped unused-parameter fix. |
| Clean rerun | `npm run typecheck` | PASS — exit 0; all four workspaces completed. |
| Clean rerun | `./gradlew build` | PASS — exit 0; `BUILD SUCCESSFUL`, 23 tasks up-to-date. |
| Final independent rerun | `npm run test`, `npm run lint`, `npm run typecheck`, `./gradlew.bat build` | PASS — 84 tests (Control Plane 39, Temporal Worker 15, Contracts 7, DB 23); lint and typecheck exit 0; Gradle `BUILD SUCCESSFUL`, 23 tasks up-to-date. |

The initial lint failure was corrected before the complete clean rerun. No test-container startup retry or Gradle loopback environment failure occurred in this verification.

## Stage 6 exclusions and known gaps

- Real `EngineActivities` adapters for Codex/Python, source control, QA, notifications, and attempt-history persistence are absent; Worker construction requires injected implementations.
- The Worker Gateway, session-affinity routing, and actual worker process deployment/startup are not implemented.
- Webhook/event ingestion and live SSE notification delivery are not connected to this engine.
- No Control Plane route, database schema, frontend wiring, or Java engine removal is included.
