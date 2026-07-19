# CP-01 Remote Project Domain Model Implementation Plan

**Goal:** Model a Project backed by a remote Git repository rather than a required local filesystem path.

**Files:**
- Modify: `src/main/java/com/example/worker/project/domain/model/RepositoryUri.java`
- Modify: `src/main/java/com/example/worker/project/domain/model/Project.java`
- Modify: `src/main/java/com/example/worker/common/exception/ErrorCode.java`
- Test: `src/test/java/com/example/worker/project/domain/model/RepositoryUriTest.java`
- Create: `src/test/java/com/example/worker/project/domain/model/ProjectTest.java`

## Acceptance criteria

- `https://`, `http://`, and `ssh://` remote repository URIs are accepted.
- `file:` and missing-scheme URIs are rejected with `PROJECT_REPOSITORY_URI_INVALID`.
- A newly created remote Project contains name, `RepositoryUri`, `BranchName`, optional credential reference, and creation time; it does not validate or require a local checkout.

## TDD steps

- [ ] Add tests for accepted HTTPS/SSH and rejected filesystem/malformed repository URIs.
- [ ] Run `./gradlew.bat :test --tests 'com.example.worker.project.domain.model.RepositoryUriTest' -x :contracts:test -x npmBuild`; expect failure before the validation change.
- [ ] Add the minimal scheme validation to `RepositoryUri`; preserve its value-object API.
- [ ] Add a `Project.createRemote(name, repositoryUri, baseBranch, credentialRef)` test that asserts no `LocalPath` construction is required.
- [ ] Add the factory and nullable legacy-path representation required for reconstitution of existing records; do not delete the legacy factory in this task.
- [ ] Re-run both domain tests; expect success.
- [ ] Run `git diff --check` and review that domain classes import no Spring/JPA/Temporal types.
- [ ] Commit only CP-01 files: `git commit -m "feat: model remote git project"`.

## Test method

Unit tests only. No database, Kafka, filesystem, or Spring context is required.
