# Issue (티켓) — SSoT

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`. 코드가 진실이며 이 문서는 그 스냅샷이다.

## Purpose
프로젝트에 속한 티켓(이슈)을 생성·조회하고 상태를 관리한다.

## Capabilities
- 프로젝트별로 이슈를 생성한다 (프로젝트 내 순번 `issueNumber` 자동 부여).
- 프로젝트의 이슈 목록을 조회한다.
- 단일 이슈를 조회한다.
- 이슈 상태를 변경한다 (임의 문자열 — §Out of scope 참고).
- 이슈 생성 시 `ISSUE_CREATED` 아웃박스 이벤트를 같은 트랜잭션으로 발행한다.

## API surface
| Method | Path | 권한 | Body |
|--------|------|------|------|
| POST | `/api/projects/:projectId/issues` | project MEMBER | `{ title(필수), description?, priority? }` |
| GET | `/api/projects/:projectId/issues` | project 조회 권한 | — |
| GET | `/api/issues/:issueId` | project 조회 권한 | — |
| PATCH | `/api/issues/:issueId/status` | project MEMBER | `{ status(필수, min 1) }` |
| POST | `/api/issues/:issueId/documents` | (document 도메인) | — |

서비스 함수 (`server/utils/issue-service.ts`):
- `createIssue(projectId, { title, description?, priority? }): IssueView`
- `listIssuesByProject(projectId): IssueView[]`
- `getIssue(issueId): IssueView | null`
- `updateIssueStatus(issueId, status): IssueView | null`

## Data model
`IssueView`: `id`, `projectId`, `issueNumber:number`, `title`, `description:string|null`, `priority:string|null`, `status`, `createdAt:ISO`.

## Invariants & rules
- `issueNumber`는 `(projectId, issueNumber)` 유니크. 생성 시 `max(issueNumber)+1`로 계산.
- 유니크 충돌(Postgres `23505`) 발생 시 순번 재계산 후 **1회** 재시도.
- 이슈 삽입과 `ISSUE_CREATED` 아웃박스 행 삽입은 **단일 트랜잭션**(`withOutbox`). id는 client-side(`randomUUID`)로 미리 생성해 aggregateId로 사용.
- 이슈 생성은 대상 프로젝트가 존재해야 함(없으면 404).

## Dependencies
- **project** 도메인 (이슈는 프로젝트에 종속, 생성 전 존재 확인).
- **document** 도메인 (`/api/issues/:issueId/documents`로 문서 첨부).
- outbox (`server/utils/outbox.ts`), db (drizzle, `@agentic-worker/db`), auth-guard (`requireProjectRole('MEMBER')`).

## Out of scope
- **상태 전이 강제 없음**: `status.patch`는 `z.string().min(1)`만 검증. CLAUDE.md의 `OPEN → IN_PROGRESS → IN_REVIEW → DONE/FAILED` 흐름은 **서버에서 강제되지 않는다**(임의 문자열 허용).
- 이슈 삭제 API 없음.
- 이슈 수정(title/description/priority) API 없음 (상태만 변경 가능).

## Source refs
- `apps/control-plane/server/utils/issue-service.ts`
- `apps/control-plane/server/api/projects/[projectId]/issues/index.post.ts`
- `apps/control-plane/server/api/projects/[projectId]/issues/index.get.ts`
- `apps/control-plane/server/api/issues/[issueId]/index.get.ts`
- `apps/control-plane/server/api/issues/[issueId]/status.patch.ts`
