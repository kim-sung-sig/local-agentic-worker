# Auth — CONTEXT (바운디드 컨텍스트)

- 상태: template
- 최종 동기화: (미동기화)

> 규약: `docs/conventions/SSOT.md`. `server/utils/auth-service.ts`, `auth-guard.ts` 기준으로 채운다.

## 경계
- 포함: 회원가입·로그인, 세션/프로젝트 권한 판정.
- 제외:

## 컨텍스트 맵
| 상대 컨텍스트 | 방향 | 관계 유형 | 통합 방식 |
|--------------|------|-----------|-----------|
| project | downstream | Published Language | `requireSession`/`requireProjectRole` 제공 |
| issue | downstream | Published Language | 동일 |

## 통합 지점

## Published language (발행 계약)
