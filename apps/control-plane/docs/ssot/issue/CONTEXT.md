# Issue — CONTEXT (바운디드 컨텍스트)

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`. 능력은 `SPEC.md`, 모델은 `MODEL.md`, 용어는 `LANGUAGE.md`.

## 경계
- 포함: 이슈(티켓)의 생성·조회·상태 변경, 프로젝트 내 순번 부여, `ISSUE_CREATED` 발행.
- 제외: 프로젝트 자체(project 컨텍스트), 문서 첨부의 저장·개정(document 컨텍스트), 알림(notification), 인증/권한 판정(auth).

## 컨텍스트 맵
| 상대 컨텍스트 | 방향 | 관계 유형 | 통합 방식 |
|--------------|------|-----------|-----------|
| project | upstream (의존) | Customer-Supplier | 생성 시 프로젝트 존재 확인, `projectId` 참조, `requireProjectRole` |
| auth | upstream (의존) | Conformist | `requireProjectRole(MEMBER)` 권한 판정에 순응 |
| document | downstream (피의존) | Customer-Supplier | `POST /api/issues/:issueId/documents`로 이슈에 문서 첨부 |
| agent 파이프라인 | downstream (피의존) | Published Language | 아웃박스 `ISSUE_CREATED` 이벤트 구독 |

## 통합 지점
- 진입 API: `SPEC.md` API surface 참조.
- 발행: `ISSUE_CREATED` (아웃박스 → 하위 워커/파이프라인).

## Published language (발행 계약)
- `ISSUE_CREATED` — payload `{ projectId, issueNumber, title }`. 스키마 상세는 `MODEL.md`.
