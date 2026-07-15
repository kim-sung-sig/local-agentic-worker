# T05 — Workspace Runtime Ownership

**Depends on:** T04  
**Goal:** Create one branch and one worktree per Workflow Run, then reuse it for all retries.

## Files

- Create: `src/main/java/com/example/worker/runtime/application/WorkspaceRuntime.java`.
- Create: `src/main/java/com/example/worker/runtime/infrastructure/git/GitWorktreeRuntime.java`.
- Create: `src/test/java/com/example/worker/runtime/infrastructure/git/GitWorktreeRuntimeTest.java`.
- Modify: `src/main/java/com/example/worker/agent/application/service/GitBranchService.java` only to delegate after parity tests pass.

## Implementation steps

- [ ] Implement `acquire(runId, branchName, baseBranch)` as an idempotent Activity operation.
- [ ] On a repeated acquire, return the existing WorkspaceRef after verifying its branch; never run `git worktree add` again.
- [ ] Implement cleanup only for terminal `COMPLETED`, `CANCELLED`, or final `FAILED` runs.
- [ ] Keep workspace paths inside the configured runtime root and reject paths outside it.

## Success criteria

- Concurrent or retried acquire requests for one run return one WorkspaceRef.
- Implementation and QA receive the same WorkspaceRef.
- A second workflow run for the same ticket has a distinct branch/worktree identity.

## Test method

- Use a temporary Git repository to test create, repeated acquire, branch mismatch rejection, and cleanup.
- Test path validation against a path traversal input.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*GitWorktreeRuntimeTest"`.
- Run: `./gradlew.bat check`.
- Review: verify command arguments are fixed arrays and no shell interpolation is used.

## Handoff record

- Attach Git fixture test output and a before/after worktree listing.
