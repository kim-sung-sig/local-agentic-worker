# T06 — Implementation, QA, and Attempt History Loop

**Depends on:** T05  
**Goal:** Run replaceable implementation and QA Activities until the configured score passes or the allowed total attempts are exhausted.

## Files

- Modify: `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflowImpl.java`.
- Create: `src/main/java/com/example/worker/engine/application/service/AttemptPolicyResolver.java`.
- Create: `src/test/java/com/example/worker/engine/application/service/AttemptPolicyResolverTest.java`.
- Modify: `src/test/java/com/example/worker/engine/workflow/AgentWorkerWorkflowTest.java`.

## Implementation steps

- [ ] Resolve `minimumQaScore` with default `90` and `maxAttempts` with default `2`, rejecting values outside `1..10`.
- [ ] Execute `ImplementationActivity` then `QualityAssuranceActivity` for each Attempt.
- [ ] Call `AttemptHistoryActivity` once for every result, including failed, error, cancelled, and passed outcomes.
- [ ] Return to Implementation only when QA score is below the configured threshold and attempts remain.

## Success criteria

- A score equal to the configured threshold passes.
- Default policy creates at most two Attempts; a ticket policy may create up to ten.
- Every Attempt has implementation artifacts, QA report reference, score, status, and timestamps.
- No retry creates a new WorkspaceRef.

## Test method

- Unit tests cover default policy, ticket override, invalid policy, threshold equality, pass-first-attempt, pass-last-attempt, and exhaustion.
- Workflow test asserts one immutable AttemptHistoryActivity call per loop iteration.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*AttemptPolicyResolverTest" --tests "*AgentWorkerWorkflowTest"`.
- Run: `./gradlew.bat check`.
- Review: confirm raw logs and diffs never enter Workflow History or JPA text columns.

## Handoff record

- Attach loop test output and a sample attempt-history timeline with artifact and QA report references.
