# [Report] Control Plane CP-04 — Issue Core Boundary

**PDCA result:** Completed at 95% match rate
**Commit boundary:** `feat: isolate issue core`

## Executive summary

| Perspective | Result |
|---|---|
| Problem | Creating an Issue required Kafka event publication and the Issue API exposed Agent-job behavior. |
| Solution | Replaced the command boundary, removed event publishing and Agent review/API dependencies, and retained Project-local Issue creation/status behavior. |
| Function effect | Remote Project owners can create and manage Issues without a worker checkout, Agent job, Kafka broker, or Temporal runtime. |
| Core value | Project and Issue now form an independently testable Control Plane core. |

## Delivered

- `CreateIssueCommand` API-to-application boundary.
- Agent/Kafka-free Issue creation and status updates with Hookify logs.
- Removal of Issue Agent-job and review endpoints/services from the Control Plane core.
- Focused tests and a static architectural-boundary scan.

## Next

Proceed to CP-05: verify Project registration, Issue creation, list/get, and status update as one Control Plane path without Agent or sync dependencies.
