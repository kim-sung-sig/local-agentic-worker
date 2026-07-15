# T03 — Versioned Activity Contracts

**Depends on:** T02  
**Goal:** Define language-neutral contracts between the Java engine and replaceable runtime workers.

## Files

- Create: `src/main/java/com/example/worker/engine/application/contract/v1/` DTO records.
- Create: `src/main/java/com/example/worker/engine/workflow/EngineActivities.java`.
- Create: `src/test/java/com/example/worker/engine/application/contract/v1/ActivityContractSerializationTest.java`.
- Create: `docs/contracts/agent-worker-activity-v1.md`.

## Implementation steps

- [ ] Define `WorkspaceRef`, `ArtifactRef`, `AttemptPolicy`, `QaResult`, and `ActivityRequestMetadata` records with a `version` field.
- [ ] Define activity methods for assessment, planning, workspace, implementation, QA, source control, notification, and attempt history.
- [ ] Require `workflowRunId`, stage, and attempt number in all commands that can mutate external state.
- [ ] Document JSON names, required fields, and idempotency key: `{workflowRunId}:{stage}:{attempt}`.

## Success criteria

- The engine can compile without importing a Claude, GitHub, Jira, or filesystem implementation.
- Every external side-effect contract includes an idempotency key.
- Large output is represented only by `ArtifactRef`, never raw log or diff content.

## Test method

- Jackson round-trip tests for every v1 request and response record.
- Reflection test rejects contract records without `version` or `ActivityRequestMetadata`.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*ActivityContractSerializationTest"`.
- Run: `./gradlew.bat check`.
- Review: confirm DTOs are records, names are stable, and no implementation-specific type crosses the contract.

## Handoff record

- Attach the generated example JSON and contract review decision.
