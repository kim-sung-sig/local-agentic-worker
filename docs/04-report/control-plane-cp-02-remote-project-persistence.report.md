# [Report] Control Plane CP-02 — Remote Project Persistence

**PDCA result:** Completed at 95% match rate
**Commit boundary:** `feat: persist remote git project`

## Executive summary

| Perspective | Result |
|---|---|
| Problem | The original schema required `local_path`, so a remote Git Project could not be persisted. |
| Solution | Added remote repository columns and dual legacy/remote entity mapping. |
| Function effect | Project persistence now round-trips a remote repository URI and credential reference without a checkout path. |
| Core value | Control Plane state is independent of the worker filesystem while existing local Project rows remain readable. |

## Delivered

- V6 non-destructive Flyway migration for remote repository columns and nullable `local_path`.
- Remote and legacy Project JPA mapping paths.
- Repository URI duplicate-check port and Spring Data named query.
- Focused Mockito tests for remote mapping, legacy mapping, and duplicate lookup.

## Verification

The focused infrastructure test suite passed. The migration's database execution is explicitly deferred to CP-05's Testcontainers verification, not inferred from a unit test.

## Next

Proceed to CP-03: expose remote Git Project registration through the Control Plane API.
