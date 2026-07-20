# [Analysis] AB-01 — Engine Notification Decoupling

**Plan:** `docs/01-plan/app-boundaries/AB-01-engine-notification-decoupling.plan.md`
**PDCA phase:** Check
**Measured match rate:** 100%

| Acceptance criterion | Evidence | Result |
|---|---|---|
| `EngineActivitiesImpl` imports nothing from `issue`/`notification` | import list in `EngineActivitiesImpl.java` | Met |
| `sendNotification` produces the same Notification shape as before (projectId, eventKey, type, severity, title, message) | `EngineNotificationConsumerTest.consume_resolvesIssueAndCreatesNotification` | Met |
| Behavior-preserving, not behavior-changing | same `idempotencyKey`/`workflowRunId` (aggregate UUID)/`ticketId` composition as the removed inline code | Met |
| New code is test-first (TDD) | `EngineNotificationRequestedTest`, `EngineActivitiesImplTest`, `EngineNotificationConsumerTest` all written before their production code | Met |
| `./gradlew check` green | full suite run, see below | Met |

## Design decision recap

Replaced `EngineActivitiesImpl`'s direct `IssueRepository`/`NotificationCommandService` calls with:

- `contracts.agentworker.EngineNotificationRequested` — a plain record, no framework types, topic
  constant colocated on the record (`TOPIC = "engine-notification-requested"`).
- `engine.application.port.NotificationPublisher` — hexagonal port, mirrors the existing
  `WorkspaceRuntime`/`SourceControlPlugin` style.
- `engine.infrastructure.notification.KafkaNotificationPublisher` — first real Kafka producer in
  this codebase (`IssueCreatedEventConsumer` previously had no matching publisher).
- `notification.infrastructure.kafka.EngineNotificationConsumer` — resolves `ticketId` to an Issue's
  `projectId` and calls the existing `NotificationCommandService.create(...)`, replicating the exact
  logic that used to live inline in `EngineActivitiesImpl`.

`EngineActivitiesImpl` dropped its "compatibility constructor" (previously used to let isolated
workflow tests skip notification wiring via nulled collaborators) — every constructor call now
requires a `NotificationPublisher`, mocked or faked in tests. This also removed a latent test gap:
the old compatibility path silently accepted a non-UUID `ticketId` in a way that would have failed
in production; the integration test's placeholder `"ticket-1"` ticketId was replaced with a real
UUID as part of this cleanup.

## Verification evidence

```text
./gradlew.bat check --no-daemon
BUILD SUCCESSFUL
144 tests, 0 failures, 0 errors, 0 skipped
```

No pre-existing unrelated failures remain in this run (the legacy `chat`/`PromptBuilder` format-string
failures and the Docker-gated integration test noted in earlier task reports did not reproduce here).

## Remaining scope (tracked in the master plan, not this task)

AB-02 through AB-05 (Gradle module skeleton, moving Control Plane/Agent Engine domains into their own
modules, and boundary verification) are not started — this task only removed the coupling that would
have blocked them.
