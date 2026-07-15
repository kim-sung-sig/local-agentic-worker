# T02 — Engine State and Persistence

**Depends on:** T01  
**Goal:** Persist the engine projection required to expose run status, stage gates, and immutable attempt history.

## Files

- Create: `src/main/java/com/example/worker/engine/domain/model/WorkflowRun.java`, `WorkflowRunId.java`, `WorkflowStage.java`, `WorkflowRunStatus.java`.
- Create: `src/main/java/com/example/worker/engine/domain/model/StageGate.java`, `GateDecision.java`, `AttemptRecord.java`, `AttemptStatus.java`.
- Create: `src/main/java/com/example/worker/engine/application/port/WorkflowRunRepository.java`, `AttemptRecordRepository.java`.
- Create: `src/main/java/com/example/worker/engine/infrastructure/datasource/*` adapters and JPA entities.
- Create: `src/main/resources/db/migration/V5__add_engine_workflow.sql`.
- Create: `src/test/java/com/example/worker/engine/domain/model/WorkflowRunTest.java`.

## Implementation steps

- [ ] Model the six ordered stages: `INTAKE`, `PLANNING`, `WORKSPACE`, `IMPLEMENTATION`, `QA`, `REVIEW_MERGE`.
- [ ] Give `WorkflowRun` exactly one nullable `WorkspaceRef`; allow it to be assigned once only.
- [ ] Persist each Attempt as a new row containing attempt number, implementation artifact references, QA report reference, score, status, and timestamps.
- [ ] Add database uniqueness for `(workflow_run_id, attempt_number)` and for the Temporal workflow ID.

## Success criteria

- A Workflow Run cannot receive a second WorkspaceRef.
- Attempt history is append-only; later attempts cannot replace prior artifact or QA references.
- Domain transitions reject an invalid stage or gate decision.

## Test method

- Unit tests cover every valid stage transition plus invalid transition rejection.
- JPA integration test with PostgreSQL Testcontainers verifies both unique constraints.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*WorkflowRunTest" --tests "*WorkflowRunRepository*Test"`.
- Run: `./gradlew.bat check`.
- Review: verify domain classes contain no Spring/JPA imports and migration is forward-only.

## Handoff record

- Attach migration output, constraint test output, and the state-transition review result.
