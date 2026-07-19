# CP-03 Project Registration API Implementation Plan

**Goal:** Register a remote Git Project through the Control Plane API.

**Files:**
- Modify: `src/main/java/com/example/worker/project/application/service/ProjectCommandService.java`
- Modify: `src/main/java/com/example/worker/project/api/request/CreateProjectRequest.java`
- Modify: `src/main/java/com/example/worker/project/api/response/ProjectResponse.java`
- Modify: `src/main/java/com/example/worker/project/application/dto/ProjectDetail.java`
- Modify: `src/main/java/com/example/worker/project/application/dto/ProjectSummary.java`
- Modify: `src/main/java/com/example/worker/project/api/controller/ProjectController.java`
- Test: `src/test/java/com/example/worker/project/application/service/ProjectCommandServiceTest.java`

## Acceptance criteria

- `POST /projects` accepts name, repository URI, base branch, and optional credential reference.
- Duplicate repository URI returns the existing business duplicate error response.
- API responses expose repository URI and base branch but never credential reference.
- The command service uses the remote Project factory; it does not call `LocalPath` validation.

## TDD steps

- [ ] Add a command-service test asserting a valid remote registration saves a remote Project.
- [ ] Add a duplicate-repository test asserting `PROJECT_ALREADY_EXISTS` (or the project duplicate error selected in CP-01) is raised.
- [ ] Run the focused test; expect failure before service changes.
- [ ] Replace the request's required `localPath` field with `repositoryUri` and optional `credentialRef` validation annotations.
- [ ] Change the command service to check `existsByRepositoryUri` and call `Project.createRemote`.
- [ ] Update response/dto factories so credential references are excluded.
- [ ] Re-run the focused test; expect success.
- [ ] Run `git diff --check` and review controller methods contain delegation only.
- [ ] Commit only CP-03 files: `git commit -m "feat: register remote git project"`.

## Test method

Mockito command-service tests. A controller contract test may be added only if the current project has an existing MockMvc pattern; otherwise CP-05 verifies the API end-to-end.
