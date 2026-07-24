# Project — LANGUAGE (유비쿼터스 언어)

- 상태: seeded
- 최종 동기화: 2026-07-24 / 커밋 9d858bd

> 규약: `docs/conventions/SSOT.md`.

| 용어 | 정의 | 코드 식별자 |
|------|------|-------------|
| Project | 개발 대상이 되는 저장소 단위 | `controlPlane.projects`, `ProjectView` |
| Repository URI | 대상 저장소 주소 (https/http/ssh) | `repositoryUri` |
| Base Branch | 작업 기준 브랜치 (기본 `main`) | `baseBranch` |
| Credential Ref | 저장소 자격증명 참조 (뷰 비노출) | `credentialRef` |
| Membership | 사용자와 프로젝트의 관계 | `controlPlane.memberships` |
| Role | 멤버십 권한 등급 | `role` |
| Owner | 프로젝트 등록자, 등록 시 자동 OWNER | `role: 'OWNER'` |
