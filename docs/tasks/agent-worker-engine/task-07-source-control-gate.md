# T07 — Draft PR and Merge Gate

**Depends on:** T06  
**Goal:** Create a Draft PR only after passing QA and merge only after a final user approval signal.

## Files

- Create: `src/main/java/com/example/worker/scm/application/SourceControlPlugin.java`.
- Create: `src/main/java/com/example/worker/scm/infrastructure/github/GitHubCliSourceControlPlugin.java`.
- Create: `src/test/java/com/example/worker/scm/infrastructure/github/GitHubCliSourceControlPluginTest.java`.
- Modify: `src/main/java/com/example/worker/agent/application/service/PullRequestService.java` only to delegate after parity tests pass.

## Implementation steps

- [ ] Define `createDraftPullRequest`, `getPullRequest`, and `mergePullRequest` operations using contract DTOs.
- [ ] Use the project base branch from Workspace metadata; do not hard-code `main`.
- [ ] Require a passed QA Attempt before Draft PR creation.
- [ ] Require the `approve` signal at Review/Merge before calling merge.

## Success criteria

- A failed or absent QA report cannot create a PR.
- A Draft PR exists before merge and an unapproved run cannot invoke merge.
- Repeated calls return the existing PR or merge result through an idempotency key.

## Test method

- Mock command runner tests command arguments, base branch propagation, idempotent existing-PR handling, and approval gate behavior.
- Workflow test proves merge Activity is not scheduled before approval.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*GitHubCliSourceControlPluginTest" --tests "*AgentWorkerWorkflowTest"`.
- Run: `./gradlew.bat check`.
- Review: inspect command construction for injection and confirm no force push or direct local merge exists.

## Handoff record

- Attach command-capture test output and a reviewer confirmation of the protected merge path.
