# 운영 워크플로 콘솔 실시간·알림 API 요청서

> 상태: 백엔드 개발 요청 (Proposed)
>
> 선행 계약: [워크플로 콘솔 조회·결정 API 요청서](operator-workflow-console-api-request.md)

## 1. 목적과 범위

운영 콘솔이 목록을 수동 새로고침하지 않고 Workflow Run의 상태 변화를 반영하고, 운영자 조치가 필요한 이벤트를 놓치지 않도록 한다.

- **실시간 스트림(SSE)**: 화면이 열려 있는 동안 상태·Attempt·결정 변경을 전달한다.
- **알림 Inbox**: 탭을 닫았거나 스트림을 놓친 경우에도 조치 필요 이벤트를 조회·읽음 처리한다.
- **브라우저 푸시, 이메일, Slack**은 이번 요청 범위에서 제외한다. Inbox와 SSE가 안정화된 뒤 같은 알림 이벤트를 소비해 추가한다.

현재 `AgentJobStreamController`의 `SseEmitter` 관례(30분 연결, 3초 재연결 힌트)는 재사용할 수 있다. 현재 Engine의 `sendNotification` Activity는 로그만 남기므로 Inbox 조회·읽음 상태·실시간 전달에는 사용할 수 없다.

## 2. 공통 규칙

### 2.1 인증·권한

API는 인증된 운영자만 호출한다. `operatorId`는 요청 본문이나 query parameter로 받지 않고 인증 주체에서 결정한다. 권한 체계가 아직 없으면 임시 운영자 컨텍스트를 사용하되, API 계약에는 사용자 식별자를 노출하지 않는다.

운영자가 접근할 수 없는 `workflowRunId`는 존재 여부와 무관하게 `404` 또는 권한 정책의 표준 오류로 처리한다.

### 2.2 시간·식별자·오류

- 모든 시간은 ISO-8601 UTC 문자열이다.
- `eventId`, `notificationId`는 정렬 가능한 불투명 문자열이며 클라이언트가 의미를 해석하지 않는다.
- 날짜·상태 이름은 서버 enum 값을 대문자 문자열로 반환한다.
- 잘못된 filter/status/stage는 `400 INVALID_REQUEST`로 반환한다.
- 결정 API의 비즈니스 오류 규칙은 기존 계약을 유지한다.

## 3. 실시간 Workflow 이벤트 스트림

### 3.1 연결

`GET /api/engine/workflow-events/stream`

응답은 `Content-Type: text/event-stream`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`를 사용한다.

| 입력 | 필수 | 설명 |
|---|---:|---|
| `Last-Event-ID` header | 아니오 | 마지막으로 정상 처리한 `eventId`. 재연결 시 누락 이벤트를 재전송한다. |
| `workflowRunId` query | 아니오 | 지정 Run만 구독한다. 없으면 접근 가능한 전체 Run을 구독한다. |
| `projectId` query | 아니오 | 프로젝트 필터. `workflowRunId`와 동시 사용 시 둘 다 만족하는 이벤트만 보낸다. |

서버는 연결 직후 `connected` 이벤트를 1회 전송하고, 유휴 중에도 15~30초마다 `heartbeat`를 전송한다. 연결 최대 수명은 30분이며, 종료 전 `retry: 3000`을 포함한다. 프런트는 오류·종료 시 3초 이상 지수 백오프로 재연결하고 마지막 성공 `eventId`를 `Last-Event-ID`로 보낸다.

### 3.2 SSE 프레임

```text
id: evt_01J...
event: workflow.updated
retry: 3000
data: {"eventId":"evt_01J...","occurredAt":"2026-07-17T02:00:00Z",...}

```

`id`와 payload의 `eventId`는 동일하다. 이벤트는 **at-least-once**로 전달될 수 있으므로 프런트는 `eventId`를 기준으로 중복 적용하지 않는다. 재개 가능한 이벤트 보관 기간은 최소 24시간이다. 보관 기간보다 오래된 `Last-Event-ID`는 `reset` 이벤트를 전송하고, 프런트가 목록·선택 상세를 다시 조회하게 한다.

### 3.3 이벤트 타입과 payload

공통 필드:

```json
{
  "eventId": "evt_01J...",
  "occurredAt": "2026-07-17T02:00:00Z",
  "workflowRunId": "4a44730a-7c7e-4c55-a77d-f520a364734a",
  "ticketId": "10c145a2-9e4c-46e4-971d-1c389e8213df"
}
```

| `event` | 발생 조건 | 추가 필드 | 프런트 처리 |
|---|---|---|---|
| `connected` | 스트림 연결 성공 | `serverTime` | 연결 표시만 갱신 |
| `heartbeat` | 유휴 연결 유지 | `serverTime` | 무시 |
| `workflow.created` | Run 시작 성공 | `currentStage`, `status`, `startedAt` | 목록에 삽입 또는 목록 재조회 |
| `workflow.updated` | 단계 또는 상태 변경 | `previousStage`, `currentStage`, `previousStatus`, `status`, `availableDecisions`, `awaitingDecision`, `updatedAt` | 목록 행·선택 상세 갱신 |
| `attempt.updated` | Attempt 시작·완료·QA 결과 변경 | `attempt` (기존 `AttemptResponse`와 같은 구조), `latestAttempt` | 목록 QA 요약·상세 이력 갱신 |
| `decision.recorded` | 승인·반려·수정 요청·재시도·취소 수락 | `decision`, `reason`, `targetStage`, `decidedAt`, `status` | 버튼 잠금 해제, 성공 피드백, 상세 재조회 |
| `reset` | 재개 토큰이 만료됨 | `reason` | 목록·선택 상세 전체 재조회 |

`workflow.updated` 예시:

```json
{
  "eventId": "evt_01J7M4Q",
  "occurredAt": "2026-07-17T02:00:00Z",
  "workflowRunId": "4a44730a-7c7e-4c55-a77d-f520a364734a",
  "ticketId": "10c145a2-9e4c-46e4-971d-1c389e8213df",
  "previousStage": "QA",
  "currentStage": "REVIEW_MERGE",
  "previousStatus": "RUNNING",
  "status": "RUNNING",
  "awaitingDecision": true,
  "availableDecisions": ["APPROVE", "REJECT", "REQUEST_REVISION", "CANCEL"],
  "updatedAt": "2026-07-17T02:00:00Z"
}
```

## 4. 운영자 알림 Inbox API

### 4.1 알림 조회

`GET /api/notifications`

| Query parameter | 필수 | 설명 |
|---|---:|---|
| `read` | 아니오 | `true` 또는 `false`; 없으면 전체 |
| `type` | 아니오 | 아래 알림 유형 필터 |
| `workflowRunId` | 아니오 | 특정 Run 알림만 조회 |
| `cursor` | 아니오 | 다음 페이지 커서 |
| `size` | 아니오 | 반환 수; 기본 20, 최대 100 권장 |

응답:

```json
{
  "items": [
    {
      "notificationId": "ntf_01J7M5A",
      "type": "APPROVAL_REQUIRED",
      "workflowRunId": "4a44730a-7c7e-4c55-a77d-f520a364734a",
      "ticketId": "10c145a2-9e4c-46e4-971d-1c389e8213df",
      "title": "QA 승인 대기",
      "message": "QA 점수 94점으로 다음 단계 승인이 필요합니다.",
      "action": { "label": "워크플로 열기", "href": "/workflow-runs/4a44730a-7c7e-4c55-a77d-f520a364734a" },
      "createdAt": "2026-07-17T02:00:00Z",
      "readAt": null
    }
  ],
  "nextCursor": null,
  "unreadCount": 3
}
```

기본 정렬은 `createdAt` 내림차순이다. `message`는 서버가 렌더링한 일반 텍스트로만 제공하며 HTML을 허용하지 않는다. `href`는 앱 내부 경로만 반환한다.

### 4.2 읽지 않은 개수

`GET /api/notifications/unread-count`

```json
{ "unreadCount": 3, "updatedAt": "2026-07-17T02:00:00Z" }
```

헤더 배지 초기 로드 및 SSE 재연결 실패 시의 보정 용도다.

### 4.3 읽음 처리

`POST /api/notifications/{notificationId}/read`

성공 시 `204 No Content`를 반환한다. 이미 읽은 알림도 `204`로 멱등 처리한다.

`POST /api/notifications/read`

```json
{ "notificationIds": ["ntf_01J7M5A", "ntf_01J7M5B"] }
```

성공 시 `204 No Content`를 반환한다. 최대 100개로 제한한다. 현재 사용자가 소유하지 않은 ID가 포함되면 전체 요청을 실패시키지 말고 해당 ID는 무시한다.

### 4.4 알림 유형과 생성 조건

| `type` | 생성 조건 | 기본 심각도 | 중복 방지 키 |
|---|---|---|---|
| `APPROVAL_REQUIRED` | 게이트에서 승인 대기 상태가 됨 | `INFO` | Run + 현재 단계 + `APPROVE` |
| `REVISION_REQUESTED` | 수정 요청 또는 반려가 기록됨 | `WARNING` | Run + 결정 eventId |
| `WORKFLOW_FAILED` | Attempt 소진 등으로 `FAILED` 종료 | `ERROR` | Run + 최종 상태 전이 eventId |
| `WORKFLOW_CANCELLED` | 운영자가 취소를 수락함 | `INFO` | Run + 취소 결정 eventId |
| `QA_RETRYING` | QA 기준 미달로 자동 재시도 시작 | `WARNING` | Run + attemptNumber |
| `DECISION_REJECTED` | 결정 요청이 서버 검증·상태 충돌로 거절됨 | `WARNING` | Run + 요청 eventId |

`APPROVAL_REQUIRED`는 같은 Run·단계에 대해 미읽음 알림이 이미 있으면 새로 만들지 않고 기존 알림을 유지한다. Run이 완료·취소·실패되면 열린 승인 대기 알림은 자동으로 읽음 처리하거나 `resolvedAt`을 추가해 UI에서 조치 완료로 표시한다. 둘 중 하나를 구현 전 확정한다.

## 5. 알림 실시간 이벤트

Workflow SSE 스트림에 아래 이벤트를 추가한다. 별도 알림 스트림을 만들지 않아 연결 수를 늘리지 않는다.

| `event` | payload | 프런트 처리 |
|---|---|---|
| `notification.created` | Notification 목록 항목과 동일한 객체 | 배지 `unreadCount` 증가, 토스트 표시, Inbox 최상단 삽입 |
| `notification.read` | `notificationId`, `readAt`, `unreadCount` | Inbox·배지 동기화 |
| `notification.unread-count` | `unreadCount`, `updatedAt` | 배지 보정 |

토스트는 `APPROVAL_REQUIRED`, `WORKFLOW_FAILED`, `DECISION_REJECTED`만 자동 노출한다. 나머지는 Inbox 배지만 갱신해 과도한 방해를 막는다.

## 6. 결정 API 보강 요청

기존 `POST /api/engine/workflow-runs/{workflowRunId}/decisions`는 유지한다. UX 안정성을 위해 아래 두 항목을 보강한다.

1. 요청 헤더 `Idempotency-Key`를 지원한다. 같은 Run·같은 키의 재전송은 최초 결과와 같은 HTTP 상태를 반환하고 Signal을 중복 전달하지 않는다.
2. `202 Accepted` 응답에 처리 추적 값을 반환한다.

```json
{
  "decisionRequestId": "dcr_01J7M6B",
  "acceptedAt": "2026-07-17T02:00:00Z"
}
```

최종 반영은 `decision.recorded` 또는 `workflow.updated` SSE 이벤트로 판단한다. 프런트는 `202`만으로 상태를 완료 처리하지 않는다. 10초 안에 해당 이벤트를 받지 못하면 단건 상세를 재조회한다.

## 7. 구현 우선순위와 완료 기준

### P0 — 화면 실사용 가능

1. 목록·상세 조회 API와 `GET /api/engine/workflow-events/stream`을 제공한다.
2. `workflow.updated`, `attempt.updated`, `decision.recorded`, `reset`을 전달한다.
3. `Last-Event-ID` 재개, 중복 eventId 허용, 24시간 이벤트 보관을 지원한다.
4. 결정 API는 `Idempotency-Key`를 처리하고 최종 상태 이벤트를 발행한다.

### P1 — 운영자 알림

1. 알림 Inbox 조회, 읽지 않은 개수, 단건·일괄 읽음 API를 제공한다.
2. `APPROVAL_REQUIRED`, `WORKFLOW_FAILED`, `QA_RETRYING` 알림을 영속한다.
3. `notification.created`, `notification.read`, `notification.unread-count`를 SSE로 전달한다.

### Acceptance Criteria

1. 새 탭에서 스트림에 연결한 뒤 Run 상태가 변경되면 3초 이내에 `workflow.updated` 이벤트를 받을 수 있다.
2. 클라이언트가 마지막 `eventId`로 재연결하면 보관 기간 안의 누락 이벤트를 순서대로 받을 수 있으며, 같은 `eventId`가 재전달돼도 상태가 중복 변경되지 않는다.
3. 재개 범위를 벗어난 경우 서버는 `reset` 이벤트를 보내고, 클라이언트는 목록·상세 재조회만으로 최신 상태를 복구할 수 있다.
4. 승인 대기·실패·QA 재시도 이벤트는 해당 운영자의 Inbox에 중복 없이 생성되고, 조회 응답의 `unreadCount`와 일치한다.
5. 읽음 API는 멱등이며, 읽음 처리 후 목록과 unread count에 즉시 반영된다.
6. 동일 `Idempotency-Key`로 결정 요청을 재전송해도 Workflow Signal은 한 번만 전달된다.
7. 모든 SSE/Inbox payload는 Workflow 도메인 엔티티를 직접 직렬화하지 않고 API 응답 DTO를 사용한다.

## 8. 백엔드 확인이 필요한 결정

1. 이벤트 저장소: Engine read model DB의 outbox 테이블을 사용할지, 별도 event store를 사용할지 결정이 필요하다.
2. 인증·권한: 운영자별 Inbox 소유자와 프로젝트 접근 범위의 정책이 필요하다.
3. `APPROVAL_REQUIRED` 해소 방식: 자동 읽음 처리와 `resolvedAt` 중 어떤 이력을 보존할지 결정이 필요하다.
4. Ticket 제목·프로젝트 메타데이터의 조인 원본은 기존 조회 계약과 함께 확정이 필요하다.
