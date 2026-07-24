# Notification — SSoT

- 상태: template
- 최종 동기화: (미동기화)

> 규약: `docs/conventions/SSOT.md`.

## Purpose
프로젝트 알림을 조회·읽음 처리하고 실시간 스트림(SSE)을 제공한다.

## Capabilities
-

## API surface
<!-- GET .../notifications, POST .../notifications/read, GET .../notifications/stream, GET .../notifications/unread-count -->

## Data model

## Invariants & rules

## Dependencies
- **project** 도메인 (`/api/projects/:projectId/notifications/**`).

## Out of scope

## Source refs
- `apps/control-plane/server/utils/notification-service.ts`
- `apps/control-plane/server/api/projects/[projectId]/notifications/`
