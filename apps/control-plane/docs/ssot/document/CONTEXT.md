# Document — CONTEXT (바운디드 컨텍스트)

- 상태: template
- 최종 동기화: (미동기화)

> 규약: `docs/conventions/SSOT.md`. 서브도메인 **revision**은 `document/revision/`.

## 경계
- 포함: 이슈에 첨부되는 문서 관리. (개정은 서브도메인 revision)
- 제외: 이슈 자체(issue).

## 컨텍스트 맵
| 상대 컨텍스트 | 방향 | 관계 유형 | 통합 방식 |
|--------------|------|-----------|-----------|
| issue | upstream | Customer-Supplier | `POST /api/issues/:issueId/documents` |
| document·revision | (서브도메인) | — | 폴더 중첩 |

## 통합 지점

## Published language (발행 계약)
