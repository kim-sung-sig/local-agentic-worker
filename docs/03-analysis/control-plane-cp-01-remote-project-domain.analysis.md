# [Analysis] Control Plane CP-01 — Remote Project Domain

**Design:** `docs/02-design/features/control-plane-cp-01-remote-project-domain.design.md`
**PDCA phase:** Check
**Measured match rate:** 100%

## Requirement-to-evidence comparison

| Design requirement | Evidence | Result |
|---|---|---|
| Allow only `https`, `http`, `ssh` URI schemes | `RepositoryUri.REMOTE_GIT_SCHEMES` | Met |
| Reject file and unsupported URI schemes | `RepositoryUriTest.rejectsLocalFileUri`, `rejectsUnsupportedUriScheme` | Met |
| Create remote Project without local filesystem access | `Project.createRemote(RemoteProjectRegistration)` passes `null` for `LocalPath` | Met |
| Avoid ambiguous factory parameters | `RemoteProjectRegistration` is the sole argument to `createRemote` | Met |
| Preserve legacy factory behavior | `Project.create` and `Project.reconstitute` retain `LocalPath` creation | Met |
| Keep credential as a reference only | `RemoteProjectRegistration` stores only `String credentialRef`, normalizing blank to `null` | Met |

## Verification evidence

```text
./gradlew.bat :test --tests 'com.example.worker.project.domain.model.*' -x :contracts:test -x npmBuild
BUILD SUCCESSFUL
```

`git diff --check` completed without whitespace errors. Contract-source scan found no framework, local-path, or filesystem dependencies in `contracts`.

## Gaps

None within CP-01. Remote Project persistence, API exposure, and work-request delivery are intentionally deferred to CP-02 through CP-04.
