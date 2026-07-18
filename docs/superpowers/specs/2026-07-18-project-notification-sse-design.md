# Project Notification SSE Design

## Status

Approved design. This document describes planned behavior; the current Engine does not yet provide these APIs.

## Goal and scope

Provide a project-scoped, durable notification Inbox and SSE stream for all workflow changes: creation, status and stage changes, Attempt changes, decisions, and Activity start, completion, and failure. Every initial notification has `publisher` set to `SYSTEM`.

External channels (Slack, SMTP), accounts, authentication, and project-team authorization are out of scope. The design keeps `projectId` on all notifications so project membership can later filter access. Read state is currently shared per project.

## Architecture

Add a `notification` bounded context with domain model, application services and ports, infrastructure persistence/SSE adapters, and thin API controllers. The Engine application layer emits a notification command for each scoped workflow event. The notification service persists a `Notification` before its post-commit SSE broadcast.

`Notification` is both the Inbox item and the replay source. It contains an opaque, ordered `eventId`, `notificationId`, `projectId`, optional `workflowRunId`, type, severity, publisher, title, plain-text message, `createdAt`, and shared `readAt`.

## API contract

- `GET /api/projects/{projectId}/notifications`: cursor-paginated Inbox; supports `read` and `type` filters.
- `GET /api/projects/{projectId}/notifications/unread-count`: returns the shared unread count.
- `POST /api/projects/{projectId}/notifications/{notificationId}/read`: idempotent shared read.
- `POST /api/projects/{projectId}/notifications/read`: idempotent shared bulk read, limited to 100 IDs.
- `GET /api/projects/{projectId}/notifications/stream`: SSE stream.

List and stream payloads are API response records, never domain entities. `message` is plain text and UI links, when added, are internal paths only.

## SSE behavior

The stream sends `connected`, `heartbeat`, `notification.created`, `notification.read`, and `reset`. Connections last 30 minutes, emit a heartbeat every 15--30 seconds, and include `retry: 3000`.

For a dropped connection, the client sends the final successfully handled `eventId` in `Last-Event-ID`. The server queries the same project for notifications after that cursor and sends them in `eventId` order before resuming live delivery. Delivery is at-least-once; clients deduplicate by `eventId`.

SSE is only live delivery. Initial screen entry uses Inbox and unread-count REST calls. If the cursor is older than the 30-day retained history, the server sends `reset`; the client reloads unread count and Inbox.

## Data retention and future accounts

Notifications are retained for 30 days, then purged. This applies to Inbox history and SSE replay. A missing connection is not an error: the persisted item is available from Inbox and a later valid cursor.

When accounts and project teams are introduced, preserve `Notification` and replace its shared `readAt` behavior with `NotificationRead(notificationId, userId, readAt)`. Project membership will filter REST and SSE queries. `SYSTEM` remains a valid system publisher.

## Error handling and verification

Unknown notification IDs in the project are ignored by bulk read; single read is idempotent for an existing project item. An inaccessible project will later use the standard project authorization response; no temporary `operatorId` is accepted from HTTP input.

Tests must cover notification creation, shared-read idempotency, ordered cursor replay, expired-cursor reset, retention boundary, and SSE reconnection contract. The implementation must also verify that a stored notification is broadcast only after transaction commit.
