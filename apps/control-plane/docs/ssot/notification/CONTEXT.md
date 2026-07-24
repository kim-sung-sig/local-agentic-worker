# Notification — CONTEXT (바운디드 컨텍스트)

- 상태: template
- 최종 동기화: (미동기화)

> 규약: `docs/conventions/SSOT.md`. `server/utils/notification-service.ts` 기준으로 채운다.

## 경계
- 포함: 프로젝트 알림 조회·읽음 처리·실시간 스트림(SSE).
- 제외:

## 컨텍스트 맵
| 상대 컨텍스트 | 방향 | 관계 유형 | 통합 방식 |
|--------------|------|-----------|-----------|
| project | upstream | Customer-Supplier | `/api/projects/:projectId/notifications` |

## 통합 지점

## Published language (발행 계약)
