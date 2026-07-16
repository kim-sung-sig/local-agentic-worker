# [Do] Control Plane CP-04 — Issue Core Boundary

- [ ] RED: add remote Project Issue creation test with no event publisher collaborator.
- [ ] GREEN: add `CreateIssueCommand` and save the Issue through core repository ports.
- [ ] RED: add nonexistent Project behavior test.
- [ ] GREEN: preserve `PROJECT_NOT_FOUND` behavior.
- [ ] REMOVE: remove Kafka/event-publisher and Agent-job API dependencies from Issue core.
- [ ] VERIFY: run focused Issue tests, import scan, and `git diff --check`.
- [ ] CHECK: compare implementation to the design.
- [ ] REPORT: record test and boundary evidence before commit/push.
