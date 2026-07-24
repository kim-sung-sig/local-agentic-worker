# Issue — LANGUAGE (유비쿼터스 언어)

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`.

| 용어 | 정의 | 코드 식별자 |
|------|------|-------------|
| Issue (이슈/티켓) | 프로젝트에 속한 하나의 작업 단위 | `controlPlane.issues`, `IssueView` |
| Issue Number | 프로젝트 내에서 이슈에 부여되는 순번 | `issueNumber` |
| Status | 이슈의 상태값 (현재 자유 문자열) | `status` |
| Priority | 이슈 우선순위 (현재 자유 문자열) | `priority` |
| ISSUE_CREATED | 이슈 생성 시 발행되는 도메인 이벤트 | `eventType: 'ISSUE_CREATED'` |
| Project | 이슈가 속하는 상위 단위 (project 컨텍스트 용어) | `projectId` |
