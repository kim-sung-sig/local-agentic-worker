# CP-02 Remote Project Persistence Implementation Plan

**Goal:** Persist a remote Project's repository URI and optional credential reference while preserving existing local-path rows during migration.

**Files:**
- Create: `src/main/resources/db/migration/V6__add_project_repository_uri.sql`
- Modify: `src/main/java/com/example/worker/project/infrastructure/datasource/ProjectJpaEntity.java`
- Modify: `src/main/java/com/example/worker/project/infrastructure/datasource/ProjectJpaRepository.java`
- Modify: `src/main/java/com/example/worker/project/infrastructure/datasource/ProjectRepositoryAdapter.java`
- Modify: `src/main/java/com/example/worker/project/application/port/ProjectRepository.java`
- Test: `src/test/java/com/example/worker/project/infrastructure/datasource/ProjectRepositoryAdapterTest.java`

## Acceptance criteria

- Migration adds nullable `repository_uri` and `credential_ref`, and changes `local_path` to nullable while preserving existing values and its uniqueness constraint.
- New remote Projects round-trip repository URI, base branch, and credential reference through the repository port.
- Duplicate remote repository registration is detected by the repository URI, independently of legacy local paths.

## TDD steps

- [ ] Add an adapter unit test with a mocked JPA repository: saving a remote Project maps `repositoryUri` and `credentialRef` to the entity.
- [ ] Run its focused Gradle test; expect failure before entity/adapter changes.
- [ ] Add the Flyway migration with nullable `repository_uri VARCHAR(500)` and `credential_ref VARCHAR(200)`, then drop only the `NOT NULL` requirement from `local_path`.
- [ ] Add entity fields and repository lookup `existsByRepositoryUri`.
- [ ] Map remote and legacy Project representations in the adapter; legacy rows must still reconstitute.
- [ ] Add and implement the matching repository-port method.
- [ ] Re-run the focused adapter test; expect success.
- [ ] Run `git diff --check`; inspect the migration for destructive SQL.
- [ ] Commit only CP-02 files: `git commit -m "feat: persist remote git project"`.

## Test method

Mockito adapter unit test. Flyway migration execution belongs to CP-05's container-backed verification, avoiding a duplicate integration fixture here.
