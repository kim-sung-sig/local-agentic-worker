# Project — CONTEXT (바운디드 컨텍스트)

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`.

## 경계
- 포함: 프로젝트(저장소) 등록·조회, 등록 시 OWNER 멤버십 부여.
- 제외: 이슈(issue), 문서(document), 알림(notification), 세션·인증 자체(auth).

## 컨텍스트 맵
| 상대 컨텍스트 | 방향 | 관계 유형 | 통합 방식 |
|--------------|------|-----------|-----------|
| auth | upstream (의존) | Conformist | `requireSession`으로 등록자 식별, `requireProjectRole`로 권한 판정 |
| issue | downstream (피의존) | Customer-Supplier | 이슈가 `projectId`로 프로젝트에 종속 (`/api/projects/:projectId/issues`) |
| notification | downstream (피의존) | Customer-Supplier | 알림이 `/api/projects/:projectId/notifications`로 종속 |

## 통합 지점
- 진입 API: `SPEC.md` API surface 참조.
- 공유 개념: `membership`(role) — auth의 사용자와 프로젝트를 잇는다.

## Published language (발행 계약)
- (현재 발행 이벤트 없음) — 하위 컨텍스트가 `projectId`/멤버십을 참조하는 방식.
