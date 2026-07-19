# Evidence: 운영 워크플로 콘솔 기능명세

## Goal

`docs/specs/SDD_operator-workflow-console.md`에 운영자가 티켓별 자동 개발 진행을 조회·결정하는 화면의 기능명세를 작성한다.

## Sources inspected

- `docs/specs/agent-worker-engine.md`
- `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflowImpl.java`
- `src/main/java/com/example/worker/engine/api/controller/WorkflowRunController.java`
- `src/main/java/com/example/worker/engine/api/request/StageDecisionRequest.java`
- `src/main/java/com/example/worker/engine/api/response/AttemptResponse.java`

## Evidence

- 6단계(`INTAKE`~`REVIEW_MERGE`), 실행 상태, 승인·반려·수정·재시도·취소 Signal을 명세에 반영했다.
- QA 기준 미달 시 남은 Attempt 동안 `IMPLEMENTATION`으로 자동 재시도하고, 소진 시 `FAILED`로 종료되는 현재 구현을 반영했다.
- 현재 API에 목록·Ticket 메타데이터·게이트 이력 조회가 없음을 확인하고, 화면 구현 전 필요한 읽기 계약으로 분리했다.

## Verification

문서 링크와 API·상태·결정 규칙을 원본 코드 및 기존 엔진 명세와 대조했다.

## Implementation verification (2026-07-17)

- `frontend/src/components/WorkflowConsole.vue`에 목록·고정 상세 패널·6단계 타임라인·시도 이력·결정 액션을 구현했다.
- 현재 백엔드는 단건 조회·시도 이력·결정 API까지만 제공하므로 목록 데이터는 `frontend/src/lib/workflow-console.js`의 목으로 유지했다.
- `REJECT.targetStage` 검증이 추가된 백엔드 계약에 맞춰 반려 사유와 대상 단계 입력을 추가했다.
- `npm test`(5개 통과), `npm run build`(통과), Browser에서 필터·승인·재시도 및 390px 반응형 레이아웃을 확인했다.
- 기존 `GET /api/projects`의 500은 워크플로 콘솔 변경과 무관한 기존 콘솔 오류로 확인했다.
