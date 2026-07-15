# T08 — Engine API, Observability, and Integration QA

**Depends on:** T07  
**Goal:** Expose engine commands and read models, then verify the full six-stage flow against Temporal and PostgreSQL.

## Files

- Create: `src/main/java/com/example/worker/engine/api/controller/WorkflowRunController.java`.
- Create: `src/main/java/com/example/worker/engine/api/request/StartWorkflowRequest.java`, `StageDecisionRequest.java`.
- Create: `src/main/java/com/example/worker/engine/api/response/WorkflowRunResponse.java`, `AttemptResponse.java`.
- Create: `src/test/java/com/example/worker/engine/api/controller/WorkflowRunControllerTest.java`.
- Create: `src/test/java/com/example/worker/engine/integration/AgentWorkerEngineIntegrationTest.java`.
- Modify: `docs/architecture/system-architecture.md` with implemented-component status only after tests pass.

## Implementation steps

- [ ] Provide APIs to start a run, query its projection and Attempt history, and send approve/reject/revision/retry/cancel commands.
- [ ] Validate stage decisions before signalling Temporal and map domain errors to the existing error response convention.
- [ ] Emit structured logs and metrics for stage transition, approval wait duration, attempt score, activity failure, and merge outcome.
- [ ] Build an integration fixture using Temporal test infrastructure and PostgreSQL Testcontainers.

## Success criteria

- API callers can inspect every Attempt's artifacts and QA report reference.
- Invalid stage decisions are rejected without mutating the Workflow Run.
- The integration test executes Intake through merged completion with approvals and verifies one reusable WorkspaceRef.

## Test method

- Controller tests cover validation and response mapping.
- Integration tests cover successful two-attempt flow, rejection/revision, Activity failure followed by manual retry, and restart recovery.

## Quality gate and review

- Run: `./gradlew.bat test`.
- Run: `./gradlew.bat check`.
- Review: verify API response records hide filesystem paths and secrets; review metrics for ticket and workflow identifiers only.

## Handoff record

- Attach integration test report, quality-check output, API contract examples, and final implementation review.
