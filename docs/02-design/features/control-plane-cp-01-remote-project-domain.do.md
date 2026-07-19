# [Do] Control Plane CP-01 — Remote Project Domain

**Precondition:** CP-01 plan and design approved.
**Implementation boundary:** domain model and focused unit tests only.

- [ ] RED: add an SSH acceptance test to `RepositoryUriTest`.
- [ ] GREEN: restrict `RepositoryUri` to `https`, `http`, and `ssh` schemes.
- [ ] RED: add a `Project.createRemote` test asserting a repository URI and no local path.
- [ ] GREEN: add `RemoteProjectRegistration` and `Project.createRemote`.
- [ ] REFACTOR: normalize a blank credential reference to `null`; do not introduce a credential value object.
- [ ] VERIFY: run focused domain tests and `git diff --check`.
- [ ] CHECK: write the CP-01 analysis after comparing code to this document.
- [ ] REPORT: write the CP-01 report after a match rate of at least 90%.
- [ ] COMMIT/PUSH: commit only CP-01 code, tests, plan/design/analysis/report documents; push the current branch.
