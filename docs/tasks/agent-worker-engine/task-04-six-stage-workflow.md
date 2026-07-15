# T04 — Six-Stage Temporal Workflow and Gates

**Depends on:** T03  
**Goal:** Orchestrate the approved six-stage flow with durable approval, rejection, revision, retry, and cancellation signals.

## Files

- Create: `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflow.java`.
- Create: `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflowImpl.java`.
- Create: `src/main/java/com/example/worker/engine/application/service/AgentWorkerStarter.java`.
- Create: `src/test/java/com/example/worker/engine/workflow/AgentWorkerWorkflowTest.java`.

## Implementation steps

- [ ] Define one `@WorkflowMethod` to start a run and signals `approve`, `reject`, `requestRevision`, `retryStage`, and `cancel`.
- [ ] Await approvals only after Intake, Planning, QA, and Review/Merge; Workspace and Implementation proceed automatically.
- [ ] Route rejection to the requested stage and persist the revision reason through an Activity.
- [ ] Use `Workflow.await`, `Workflow.currentTimeMillis`, and Activities; do not use `Thread`, `Instant.now`, file I/O, or repository calls.

## Success criteria

- A run cannot enter the next gated stage before `approve` is signalled.
- A rejection preserves the reason and returns to the requested stage.
- A workflow replay produces the same ordered Activity commands.

## Test method

- `TestWorkflowEnvironment` tests approval, rejection, revision, cancellation, and recovery after worker restart.
- Replay test covers a completed run history.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*AgentWorkerWorkflowTest"`.
- Run: `./gradlew.bat check`.
- Review: inspect the workflow for Temporal non-determinism and confirm all I/O is delegated to Activities.

## Handoff record

- Attach signal test results, replay test result, and the approved state-transition diagram.
