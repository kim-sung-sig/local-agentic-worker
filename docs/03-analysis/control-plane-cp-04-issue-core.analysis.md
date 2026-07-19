# [Analysis] Control Plane CP-04 — Issue Core Boundary

**Design:** `docs/02-design/features/control-plane-cp-04-issue-core.design.md`
**PDCA phase:** Check
**Measured match rate:** 95%

| Design requirement | Evidence | Result |
|---|---|---|
| Create Issue for remote Project without local path | `IssueCommandServiceTest.savesOpenIssueWithoutAgentOrKafka` | Met |
| Missing Project is rejected | `IssueCommandServiceTest.rejectsMissingProject` | Met |
| Status update remains a core use case | `IssueCommandServiceTest.savesIssueWithNextStatus` | Met |
| Controller uses only Issue services | `IssueController` constructor and imports | Met |
| No Agent-job endpoint | `/agent-job` removed from `IssueController` | Met |
| No Kafka/event-publisher collaborator in Issue command service | two-port constructor in `IssueCommandService` | Met |
| Issue application/API are Agent/Kafka/Temporal independent | static import scan passed | Met |

## Verification evidence

```text
./gradlew.bat :test --tests 'com.example.worker.issue.*' -x :contracts:test -x npmBuild
BUILD SUCCESSFUL
```

The core-package import scan returned no `agent`, `kafka`, or `io.temporal` import. `git diff --check` passed.

## Remaining verification gap

Repository-backed list/get behavior and HTTP serialization are unchanged and are exercised in CP-05's remote Project-to-Issue integration test. Legacy Agent event model classes remain only to avoid rewriting the excluded Agent subsystem; Issue application and API do not reference them.
