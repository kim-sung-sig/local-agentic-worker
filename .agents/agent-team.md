# 개발 에이전트 팀 공통 계약

이 문서는 Claude와 Codex가 공유하는 역할·권한·협업 기준이다. 각 도구의 `.claude/agents/`와 `.codex/agents/` 정의는 이 계약을 따라야 한다.

Agent Worker의 격리 실행·idempotency·repository harness 신뢰 경계는 [agent-worker-runtime.md](agent-worker-runtime.md)를 추가로 따른다.

| 역할 | 권한 | 책임 |
|---|---|---|
| `code-explorer` | 읽기 전용 | 영향 범위, 계약, 기존 패턴·테스트 탐색 |
| `backend-implementer` | 수정 | Java/Spring 구현과 집중 테스트 |
| `frontend-implementer` | 수정 | Vue 화면·연동과 집중 테스트 |
| `backend-reviewer` | 읽기 전용 | 계층, 계약, 오류, 테스트 누락 검토 |
| `frontend-reviewer` | 읽기 전용 | 동작, 화면 상태, 접근성, 계약 검토 |
| `orchestrator` | 조율 | 탐색 → 필요한 구현 → 독립 리뷰 → 최소 수정 |

## 공통 규칙

- 구현자는 요청 범위 밖 리팩터링·의존성 추가를 하지 않는다.
- 리뷰어와 탐색자는 파일을 수정하지 않으며, 근거와 재현 조건이 있는 지적만 남긴다.
- 백엔드는 `domain → application → api` 경계를 지키고, 프론트는 기존 Vue 패턴을 재사용한다.
- 오케스트레이터는 탐색 후 필요한 역할만 호출한다. 독립적인 백엔드·프론트 작업 및 리뷰는 병렬로 실행할 수 있다.
- 역할 실패는 한 번 재시도하고, 재실패는 누락으로 보고하되 독립 작업은 계속한다.
- 리뷰 결과가 없으면 승인으로 처리하지 않는다.
