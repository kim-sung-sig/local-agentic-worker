# Workflow Kanban Console Design

## Goal

운영자가 Workflow Run의 병목과 승인 대기를 빠르게 판단하도록 기존 표 중심 콘솔을 칸반 보드 중심 화면으로 교체한다.

## Information architecture

- 상단: 페이지 제목, 프로젝트 컨텍스트, 검색·상태 필터, 읽지 않은 알림 배지.
- 본문: 아래 5개 레인으로 Workflow Run 카드를 표시한다.
  1. 승인 대기 — `INTAKE`, `PLANNING` 게이트
  2. 자동 실행 — `WORKSPACE`, `IMPLEMENTATION`
  3. QA — `QA`
  4. 검토·병합 — `REVIEW_MERGE`
  5. 종료 — `COMPLETED`, `FAILED`, `CANCELLED`
- 카드: 티켓 ID, 제목(현재 목 데이터), 세부 단계, 상태, 최신 QA 점수/Attempt, 조치 필요 표시만 노출한다.
- 상세: 카드를 선택하면 우측 드로어에 6단계 타임라인, Attempt 이력과 허용된 결정 액션을 표시한다.
- 알림: 헤더 배지와 Inbox 패널을 두고, 구현된 프로젝트별 알림 SSE의 `notification.created/read/reset`을 소비한다.

## State and data boundaries

- 현재 Workflow Run 목록 API가 없으므로 보드 데이터는 기존 목 데이터를 계속 사용한다.
- 구현된 SSE는 프로젝트별 알림 전용이다. 알림 배지·Inbox만 실시간으로 갱신하며, 카드 상태의 실시간 갱신은 `workflow.updated` API가 제공될 때 추가한다.
- 기존 단건·Attempt·결정 API와의 연결은 별도 API 연동 작업으로 유지한다.

## Interaction

- 카드 선택은 드로어를 연다. 닫으면 보드 문맥을 유지한다.
- 검색·상태 필터는 모든 레인에 적용한다.
- 승인·반려·수정 요청·재시도·취소의 기존 입력 검증 규칙을 유지한다.
- 모바일에서는 레인을 가로 스크롤하고 상세 드로어는 하단 패널로 전환한다.

## Acceptance criteria

1. 모든 Run은 상태/단계에 따라 정확히 하나의 레인에 표시된다.
2. 카드는 운영 판단에 필요한 최소 정보와 조치 필요 여부를 보여준다.
3. 카드를 선택하면 기존 6단계 이력과 결정 액션을 확인할 수 있다.
4. 검색·상태 필터 결과가 모든 레인에 일관되게 반영된다.
5. 390px 너비에서 레인 및 상세 패널의 내용이 잘리지 않는다.
6. 알림 Inbox는 SSE 연결이 가능할 때 새 알림·읽음 상태를 반영하며, 연결되지 않아도 보드는 동작한다.

## Scope exclusions

- 드래그 앤 드롭으로 상태를 직접 이동하지 않는다. 상태 전이는 Workflow 결정 API를 통해서만 발생한다.
- 브라우저 푸시·메일·Slack 알림은 제외한다.
