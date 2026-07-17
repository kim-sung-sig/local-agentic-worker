# Control Plane Code Review Remediation Report

## Reviewed range

`origin/main` (`f656bb3`) through `98506d9`.

## Resolved findings

| Severity | Finding | Resolution |
|---|---|---|
| Critical | Credential-bearing repository URLs could be stored, logged, and returned. | `RepositoryUri` now rejects user-info, query, and fragment components; only `ssh://git@host/...` retains the standard non-secret Git SSH user. |
| Important | URI scheme validation accepted no-host values. | A host is now required. |
| Important | `MAX(issue_number) + 1` was concurrent-create unsafe. | Issue creation locks its Project row with `PESSIMISTIC_WRITE` before allocating the next number. |
| Important | V6 migration/JPA behavior was only mocked. | Added a Docker-conditional PostgreSQL/Flyway integration test for remote Project persistence and concurrent Issue allocation. |
| Minor | Request length exceeded schema limits could produce a database error. | Added Bean Validation limits for Project fields. |

## Verification

```text
./gradlew.bat :test --tests 'com.example.worker.project.*' --tests 'com.example.worker.issue.*' --tests 'com.example.worker.controlplane.*' -x :contracts:test -x npmBuild
BUILD SUCCESSFUL
```

The PostgreSQL integration test skips gracefully where Docker is unavailable and executes when a Docker daemon is available.

## Review exception

The reviewer recommended rejecting all URI user-info. This implementation permits only `ssh://git@host/...`, because it is a normal Git SSH identity rather than a credential; all other user-info is rejected.
