# [Analysis] Control Plane CP-02 — Remote Project Persistence

**Design:** `docs/02-design/features/control-plane-cp-02-remote-project-persistence.design.md`
**PDCA phase:** Check
**Measured match rate:** 95%

## Requirement-to-evidence comparison

| Design requirement | Evidence | Result |
|---|---|---|
| Remote Project maps without `local_path` | `ProjectRepositoryAdapterTest.preservesRemoteRepositoryFields` | Met |
| Legacy local Project still maps | `ProjectRepositoryAdapterTest.preservesLegacyLocalPath` | Met |
| Remote URI and credential reference round-trip | `ProjectJpaEntity.from` and `toDomain` | Met |
| Duplicate remote URI has a repository-port operation | `ProjectRepository.existsByRepositoryUri` and adapter delegation test | Met |
| Existing rows are preserved while remote rows are possible | `V6__add_project_repository_uri.sql` alters only `local_path` nullability and adds columns/index | Design reviewed |

## Verification evidence

```text
./gradlew.bat :test --tests 'com.example.worker.project.infrastructure.datasource.ProjectRepositoryAdapterTest' -x :contracts:test -x npmBuild
BUILD SUCCESSFUL
```

`git diff --check` completed without whitespace errors. The contract-source scan has no prohibited framework, local-path, or filesystem dependency.

## Remaining verification gap

The V6 migration has been statically reviewed but has not run against PostgreSQL in this task. CP-05 owns container-backed migration execution and direct Project-to-Issue flow verification. This is outside CP-02's unit-test boundary and does not block its 95% PDCA gate.
