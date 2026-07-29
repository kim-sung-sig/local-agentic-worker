# 개발 에이전트 팀 공통 계약

이 문서는 Claude와 Codex가 공유하는 역할·권한·협업 기준이다. 각 도구의 `.claude/agents/`와 `.codex/agents/` 정의는 이 계약을 따라야 한다.

Agent Worker의 격리 실행·idempotency·repository harness 신뢰 경계는 [agent-worker-runtime.md](agent-worker-runtime.md)를 추가로 따른다.

| 역할 | 권한 | 책임 |
|---|---|---|
| `backend-planner` | 읽기 전용 | 요구사항을 구현·검증 가능한 작업으로 분해 |
| `backend-developer` | 수정 | 단일 작업 구현과 집중 검증 |
| `backend-reviewer` | 읽기 전용 | 명세·품질 이중 판정과 최종 broad 리뷰 |
| `backend-orchestrator` | 조율 | 계획 → 작업별 구현·리뷰·수정 → 통합 보고 |
| `build-verify-agent` | 읽기 전용 | 빌드·테스트 검증 결과 확인 |
| `test-qa-agent` | 읽기 전용 | 사용자 흐름 기반 QA 확인 |
| `verify-qa-reporter` | 읽기 전용 | 검증 결과를 통합 보고 |

## 공통 규칙

- 구현자는 요청 범위 밖 리팩터링·의존성 추가를 하지 않는다.
- 리뷰어와 검증 역할은 파일을 수정하지 않으며, 근거와 재현 조건이 있는 지적만 남긴다.
- 한 작업은 한 책임·한 커밋으로 관리하고, 실패 테스트를 먼저 확인한 뒤 최소 구현으로 통과시킨다.
- 오케스트레이터는 각 작업의 구현 보고와 리뷰 승인을 확인한 뒤에만 다음 작업으로 진행한다.
- 역할 실패는 한 번 재시도하고, 재실패는 누락으로 보고하되 독립 작업은 계속한다.
- 리뷰 결과가 없으면 승인으로 처리하지 않는다.
