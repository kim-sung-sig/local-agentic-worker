# Project — MODEL (도메인 모델)

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`. 용어 정의는 `LANGUAGE.md`. API 응답 DTO(`ProjectView`)는 `SPEC.md`.

## Aggregates
- **Project** (루트) — 개발 대상 저장소. 불변식:
  - 등록과 등록자 `OWNER` 멤버십 생성은 **단일 트랜잭션**.
  - `repositoryUri` 스킴은 `https|http|ssh` 중 하나.
  - `baseBranch` 미지정 시 `'main'`.

## Entities
- Project: `id`, `name`, `repositoryUri?`, `baseBranch`, `credentialRef?`, `createdAt`.
- Membership: `userId`, `projectId`, `role`.

## Value Objects
- **RepositoryUri** — URL + 스킴 제약(https/http/ssh). 현재 zod refine으로 검증(전용 타입은 아님).
- **Role** — 멤버십 역할. 관측된 값: `OWNER`(등록 시), `MEMBER`(권한 판정 기준).

## 도메인 이벤트
- (현재 없음)

## 영속 매핑 (테이블)
- `controlPlane.projects` ↔ Project.
- `controlPlane.memberships` ↔ Membership (project·user 연결).
