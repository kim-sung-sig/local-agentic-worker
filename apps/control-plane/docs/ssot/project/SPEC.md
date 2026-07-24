# Project — SSoT

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`. 코드가 진실이며 이 문서는 그 스냅샷이다.

## Purpose
개발 대상 저장소(프로젝트)를 등록·조회한다. 등록자는 자동으로 OWNER가 된다.

## Capabilities
- 프로젝트를 등록한다 (등록자에게 `OWNER` 멤버십을 같은 트랜잭션으로 부여).
- 로그인 사용자가 속한 프로젝트 목록을 조회한다 (멤버십 기준).
- 단일 프로젝트를 조회한다.

## API surface
| Method | Path | 권한 | Body |
|--------|------|------|------|
| POST | `/api/projects` | 세션 필요 | `{ name(필수), repositoryUri(필수), baseBranch?, credentialRef? }` |
| GET | `/api/projects` | 세션 필요 | — (본인 멤버십 프로젝트만) |
| GET | `/api/projects/:projectId` | project 조회 권한 | — |

서비스 함수 (`server/utils/project-service.ts`):
- `registerProject({ name, repositoryUri, baseBranch?, credentialRef? }, ownerUserId): ProjectView`
- `listProjects(userId): ProjectView[]`
- `getProject(projectId): ProjectView | null`

## Data model
`ProjectView`: `id`, `name`, `repositoryUri:string|null`, `baseBranch`(기본 `'main'`), `createdAt:ISO`.
저장되지만 뷰에 노출되지 않음: `credentialRef`.

## Invariants & rules
- `repositoryUri`는 URL이며 스킴이 `https` / `http` / `ssh` 중 하나여야 함(zod refine).
- `baseBranch` 미지정 시 `'main'`.
- 프로젝트 등록과 `OWNER` 멤버십 삽입은 **단일 트랜잭션**.
- `listProjects`는 `memberships` 이너 조인으로 본인 소속 프로젝트만 반환.

## Dependencies
- **membership**(`controlPlane.memberships`) — 등록 시 OWNER 부여, 목록 조회 기준.
- db (drizzle), auth-guard (`requireSession`, `requireProjectRole`).
- 하위: **issue**, **notification** 도메인이 `/api/projects/:projectId/...`로 연결됨.

## Out of scope
- 프로젝트 수정/삭제 API 없음.
- 멤버십 관리 API 없음 (등록 시 OWNER 자동 부여가 유일한 경로).

## Source refs
- `apps/control-plane/server/utils/project-service.ts`
- `apps/control-plane/server/api/projects/index.post.ts`
- `apps/control-plane/server/api/projects/index.get.ts`
- `apps/control-plane/server/api/projects/[projectId]/index.get.ts`
