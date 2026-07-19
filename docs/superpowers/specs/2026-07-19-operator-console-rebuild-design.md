# Operator Console Rebuild Design

> Supersedes the table-based workflow console UI. This is a frontend information-architecture replacement, not an incremental visual refresh.

## Goal

운영자가 대시보드에서 프로젝트 상태를 파악하고, 프로젝트 보드에서 티켓 병목을 확인하며, 티켓 드로어에서 자동 개발 워크플로를 승인·반려할 수 있는 새 운영 콘솔을 제공한다.

## Navigation and screens

### 1. Dashboard (`/`)

- 프로젝트 카드: 이름, 저장소, 열린 티켓 수, 조치 필요 수. 각 프로젝트의 Issue API를 독립 호출하며, 한 프로젝트 조회가 실패하면 해당 카드의 두 수치는 `-`로 표시한다.
- 열린 티켓 수는 종료(`DONE`, `COMPLETED`, `CANCELLED`)가 아닌 Issue 수다. 조치 필요는 Workflow 결정 상태가 아직 없으므로 `IN_REVIEW` 또는 `FAILED`인 Issue를 표시하는 UI 휴리스틱이다.
- 운영 큐: 승인 대기·수정 필요·실패 티켓을 우선 표시한다.
- 프로젝트 카드를 선택하면 `/projects/:projectId/board`로 이동한다.

### 2. Project board (`/projects/:projectId/board`)

보드는 다음 6개 레인을 사용한다. 카드가 한 레인에만 속하도록 `issue.status`와 Workflow 상태를 우선순위로 매핑한다.

| Lane | 포함 조건 | 운영 의미 |
|---|---|---|
| 승인 대기 | 연결된 Run의 결정 대기 상태 | 운영자 승인 필요 |
| 개발 중 | `OPEN`, `IN_PROGRESS`, 구현 중 Run | 자동 작업 진행 |
| QA | QA 단계 Run | 검증 중 |
| 리뷰·병합 | 리뷰 단계 Run 또는 `IN_REVIEW` | 최종 검토 |
| 수정 필요 | `PAUSED`, `FAILED` 또는 반려된 Issue | 재시도·수정 필요 |
| 완료 | `DONE`, `COMPLETED`, `CANCELLED` | 종료 상태 |

현재 Issue API에는 결정 대기 상태가 없으므로 승인 대기 레인은 비어 있고 “티켓 없음”으로 표시한다. `OPEN`은 실제 승인 근거가 없으므로 개발 중 레인에 매핑한다.

카드는 티켓 번호·제목·우선순위·Issue 상태를 항상 표시한다. `workflowStage`가 없으면 “워크플로 단계 연동 대기”, QA 점수나 Attempt가 없으면 “QA 연동 대기”를 표시하며 수치나 단계를 추정하지 않는다. `IN_REVIEW`·`FAILED`는 “조치 필요”를 표시한다. 드래그 앤 드롭은 지원하지 않는다. 상태 변경은 엔진 결정 또는 기존 Issue 상태 API를 통해서만 수행한다.

### 3. Issue drawer

카드를 선택하면 우측 드로어를 열고 다음을 보여 준다.

- 제목, 설명, 우선순위, Issue 상태
- 6단계 Agent 진행 타임라인과 Attempt 이력
- 승인, 수정 요청, 반려, 재시도, 취소
- 반려 대상 단계와 수정·반려 사유 입력

드로어는 `workflowRunId`가 있는 티켓에서만 Engine 결정 버튼을 활성화한다. Run 연결이 없는 티켓은 “워크플로 시작 전” 상태와 비활성 이유를 표시한다. 이 제약은 존재하지 않는 API를 추측 호출하지 않기 위한 것이다.

### 4. Notifications

프로젝트 헤더에는 unread badge와 Inbox 패널을 둔다. 구현된 `GET /api/projects/{projectId}/notifications/stream`을 사용하여 `notification.created`, `notification.read`, `reset`을 처리한다. `eventId` 기준 중복 제거를 하고 `reset`에서는 Inbox 목록과 unread count를 다시 조회한다. SSE 이벤트는 현재 보드 카드 상태를 변경하지 않는다.

## Data contracts

| Data | Source | UI use |
|---|---|---|
| 프로젝트 목록 | `GET /api/projects` | Dashboard, navigation |
| 프로젝트 이슈 | `GET /api/projects/{projectId}/issues` | Board cards |
| Issue 상세·상태 | `GET/PATCH /api/issues/{id}` | Drawer summary, non-workflow updates |
| Workflow 단건·Attempt·결정 | `/api/engine/workflow-runs/{id}` | Drawer only when mapping exists |
| Inbox·SSE | `/api/projects/{projectId}/notifications*` | Project header Inbox |

프로젝트별 Workflow Run 목록 및 Issue–Run 매핑은 현재 없다. 프런트는 `issue.workflowRunId`가 응답에 추가될 때 즉시 소비할 수 있는 adapter를 사용하고, 그 전에는 workflow action을 안전하게 비활성화한다.

현재 Issue API에는 Workflow 단계 정보가 없으므로 QA 레인은 Issue–Run 매핑 또는 `workflowStage` 보강 응답이 제공된 뒤에만 티켓을 표시한다. 그 전에는 카드와 레인에서 연동 대기 상태를 표시한다.

## UI system

- 기존 페이지·사이드바·표 스타일은 사용하지 않는다.
- 밝은 중립 배경, 고정 사이드 내비게이션, 넓은 보드 캔버스, 레인별 연한 배경을 사용한다.
- 색은 우선순위와 조치 필요 상태에만 쓰고, 상태 정보는 항상 텍스트도 함께 표시한다.
- 데스크톱은 드로어, 모바일은 하단 패널과 가로 스크롤 보드를 사용한다.

## Error handling

- 프로젝트/이슈 조회 실패는 해당 화면 안의 재시도 버튼과 오류 문구를 표시한다.
- SSE 연결 실패는 Inbox에 “실시간 연결 끊김”만 표시하며 보드 사용을 막지 않는다.
- 결정 요청은 진행 중 중복 제출을 막고, 검증 오류·서버 오류를 액션 영역에 표시한다.

## Acceptance criteria

1. Dashboard에서 프로젝트별 운영 현황을 보고 프로젝트 보드로 이동할 수 있다.
2. Project board에서 모든 프로젝트 이슈가 정확히 하나의 레인에 표시된다.
3. 티켓 카드 선택으로 드로어를 열고 닫아도 보드 필터와 스크롤 문맥이 유지된다.
4. 연결된 Workflow Run에서만 승인·반려·수정 요청·재시도·취소를 보낼 수 있다.
5. 반려는 대상 단계, 수정 요청·반려는 사유 없이 제출할 수 없다.
6. SSE 재연결로 같은 알림이 재전달돼도 Inbox 항목·unread count가 중복되지 않는다.
7. 390px 너비에서 레인, 카드, 드로어의 조작·텍스트가 잘리지 않는다.

## Out of scope

- 드래그 앤 드롭 상태 이동
- 브라우저 푸시·메일·Slack
- 프로젝트별 Workflow Run 목록 API와 Issue–Run 매핑의 백엔드 구현
