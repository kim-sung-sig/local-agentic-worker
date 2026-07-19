# 운영 워크플로 콘솔 API 요청서

> 상태: 백엔드 개발 요청
>
> 관련 기능명세: [운영 워크플로 콘솔 SDD](../specs/SDD_operator-workflow-console.md)

## 목적

운영 콘솔이 티켓별 Workflow Run을 목록으로 조회하고, 선택한 Run의 진행 상태·게이트 이력·Attempt 이력을 표시할 수 있도록 읽기 API를 제공한다. 기존 결정 API(`POST /api/engine/workflow-runs/{workflowRunId}/decisions`)는 유지한다.

## 현재 제공 API

| API | 한계 |
|---|---|
| `GET /api/engine/workflow-runs/{workflowRunId}` | 단건 상태만 반환하며 Ticket ID·시작 시각·목록 조회가 없다. |
| `GET /api/engine/workflow-runs/{workflowRunId}/attempts` | Attempt 이력은 제공하지만 목록 화면의 최신 점수 요약에는 별도 호출이 필요하다. |
| `POST /api/engine/workflow-runs/{workflowRunId}/decisions` | 결정 전송은 가능하지만, 게이트 대기 여부·반려 사유를 다시 읽을 수 없다. |

`engine_workflow_run`에는 `ticket_id`, `temporal_workflow_id`, `current_stage`, `status`, `started_at`, `finished_at`이 이미 저장된다. Ticket 제목·프로젝트·우선순위는 Engine 저장 모델에 없다.

## 요청 API

### 1. Workflow Run 목록

`GET /api/engine/workflow-runs`

| Query parameter | 필수 | 설명 |
|---|---:|---|
| `status` | 아니오 | `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED` 필터 |
| `stage` | 아니오 | `INTAKE`~`REVIEW_MERGE` 필터 |
| `ticketId` | 아니오 | 특정 Ticket의 Run 필터 |
| `cursor` | 아니오 | 커서 기반 다음 페이지 조회 |
| `size` | 아니오 | 반환 개수; 기본·최대값은 백엔드 정책 |

응답 예시:

```json
{
  "items": [
    {
      "workflowRunId": "4a44730a-7c7e-4c55-a77d-f520a364734a",
      "ticketId": "10c145a2-9e4c-46e4-971d-1c389e8213df",
      "currentStage": "QA",
      "status": "PAUSED",
      "startedAt": "2026-07-17T01:10:00Z",
      "finishedAt": null,
      "latestAttempt": {
        "attemptNumber": 1,
        "qaScore": 86,
        "status": "PASSED"
      }
    }
  ],
  "nextCursor": null
}
```

정렬은 `startedAt` 내림차순을 기본값으로 한다. `latestAttempt`가 없으면 `null`을 반환한다.

### 2. Workflow Run 상세 확장

기존 단건 API를 아래 응답으로 확장하거나, 호환성을 위해 `GET /api/engine/workflow-runs/{workflowRunId}/detail`을 추가한다.

```json
{
  "workflowRunId": "4a44730a-7c7e-4c55-a77d-f520a364734a",
  "ticketId": "10c145a2-9e4c-46e4-971d-1c389e8213df",
  "currentStage": "QA",
  "status": "PAUSED",
  "startedAt": "2026-07-17T01:10:00Z",
  "finishedAt": null,
  "awaitingDecision": true,
  "availableDecisions": ["APPROVE", "REJECT", "REQUEST_REVISION", "CANCEL"],
  "latestGate": {
    "stage": "QA",
    "decision": "REQUEST_REVISION",
    "reason": "회귀 테스트 보강 필요",
    "decidedAt": "2026-07-17T01:20:00Z"
  }
}
```

`availableDecisions`는 서버가 현재 상태·단계에 따라 계산한다. UI는 이 값에 없는 결정을 노출하지 않는다.

## Ticket 표시 범위

1차 화면은 `ticketId`만 표시한다. Ticket 제목·프로젝트·우선순위 표시는 Ticket 도메인 조회 계약이 확정된 뒤 별도 추가한다.

## 완료 기준

1. 상태·단계·Ticket ID로 Workflow Run 목록을 커서 기반 조회할 수 있다.
2. 목록의 각 항목은 최신 Attempt 요약을 포함한다.
3. 상세 응답은 현재 결정 가능 여부와 최신 게이트 이력을 포함한다.
4. 목록·상세 응답은 Engine domain 모델을 API에 직접 노출하지 않는 `record` 응답 DTO를 사용한다.
