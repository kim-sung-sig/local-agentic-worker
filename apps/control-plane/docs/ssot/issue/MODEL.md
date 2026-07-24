# Issue — MODEL (도메인 모델)

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`. 용어 정의는 `LANGUAGE.md`. API 응답 DTO(`IssueView`)는 `SPEC.md`.

## Aggregates
- **Issue** (루트) — 하나의 티켓. 불변식:
  - `issueNumber`는 `(projectId, issueNumber)` 유니크, 프로젝트 내 `max+1`.
  - 생성은 `ISSUE_CREATED` 아웃박스 행과 **단일 트랜잭션**.

## Entities
- Issue: `id(uuid, client-side 생성)`, `projectId`, `issueNumber(int)`, `title`, `description?`, `priority?`, `status`, `createdAt`.

## Value Objects
- (현재 없음) `status`, `priority`는 자유 문자열로 저장되며 VO/enum으로 강제되지 않는다 → 향후 모델링 후보.

## 도메인 이벤트
- **ISSUE_CREATED** — `aggregateType: 'issue'`, `aggregateId: <issue.id>`, payload `{ projectId, issueNumber, title }`.

## 영속 매핑 (테이블)
- `controlPlane.issues` ↔ Issue Aggregate.
- 참조: `projectId` → `controlPlane.projects.id` (project 컨텍스트 소유).
