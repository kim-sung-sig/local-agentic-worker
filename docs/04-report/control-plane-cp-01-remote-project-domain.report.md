# [Report] Control Plane CP-01 — Remote Project Domain

**PDCA result:** Completed
**Commit boundary:** `feat: model remote git project`

## Executive summary

| Perspective | Result |
|---|---|
| Problem | A Project required an existing local directory, preventing remote Git-first registration. |
| Solution | Added a remote Project domain path with a validated repository URI and optional credential reference. |
| Function effect | Callers can construct a remote Project without a filesystem checkout. |
| Core value | The Control Plane can now model its source repository independently from an Agent worker machine. |

## Delivered

- Remote repository URI validation for `https`, `http`, and `ssh`.
- Explicit rejection for `file:` and unsupported URI schemes.
- `RemoteProjectRegistration` command value and `Project.createRemote` factory.
- Blank credential-reference normalization and focused unit-test coverage.

## Verification

The CP-01 domain test suite passed. Full-suite failures remain outside this task: one PostgreSQL-dependent Spring context test and six legacy Agent tests. They were neither changed nor used as evidence for CP-01 completion.

## Next

Proceed to CP-02: persist remote repository URI and credential reference while retaining compatibility with existing local-path rows.
