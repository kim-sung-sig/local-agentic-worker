# Agent Worker Engine — Activity Contract v1

> 이 문서는 `com.example.worker.engine.application.contract.v1` 패키지의 DTO와 `com.example.worker.engine.workflow.EngineActivities` 인터페이스가 정의하는 엔진↔Worker 계약을 설명한다. 엔진은 이 계약(Task Queue 이름 + record)으로만 외부와 통신하며, Claude/GitHub/Jira/파일시스템 등 구현체 타입을 직접 참조하지 않는다.

## 버전 정책

- 모든 계약 record는 마지막 필드로 `int version`을 가진다. 필드를 추가/변경할 때는 새 버전 패키지(`contract.v2`)를 만들고, 기존 `v1`은 하위 호환을 위해 그대로 유지한다.
- 외부 상태를 변경할 수 있는 모든 Request record는 첫 필드로 `ActivityRequestMetadata metadata`를 포함한다.

## 멱등키

모든 mutating 요청은 `ActivityRequestMetadata.idempotencyKey()`로 아래 형식의 멱등키를 계산한다.

```
{workflowRunId}:{stage}:{attemptNumber}
```

예: `run-42:QA:3` — 같은 Workflow Run의 같은 Stage/Attempt에 대한 재시도는 동일한 키를 가지므로, Worker 구현체는 이 키로 중복 실행을 감지해야 한다.

## 공통/값 객체

| Record | 필드 | 설명 |
|--------|------|------|
| `ActivityRequestMetadata` | `workflowRunId`(String), `stage`(WorkflowStage), `attemptNumber`(int), `version`(int) | 모든 mutating Request에 포함되는 멱등성/추적 메타데이터 |
| `WorkspaceRef` | `value`(String), `version`(int) | Runtime이 소유한 worktree를 가리키는 불투명 참조. 로컬 경로를 엔진에 노출하지 않는다 |
| `ArtifactRef` | `value`(String), `kind`(String), `version`(int) | 대용량 산출물(로그/diff/리포트 원문)에 대한 참조. 원문은 Temporal History에 저장하지 않는다 |
| `AttemptPolicy` | `maxAttempts`(int), `minimumQaScore`(int), `version`(int) | Ticket/프로젝트 정책이 오버라이드하는 재시도 정책 (`maxAttempts` 1~10, 기본 2) |
| `QaResult` | `passed`(boolean), `score`(int), `reportRef`(ArtifactRef), `version`(int) | QA Activity의 결과값 |

## Activity 목록 (`EngineActivities`)

| Activity 메서드 | Request | Response | 책임 |
|---|---|---|---|
| `assessTicket` | `TicketAssessmentRequest(metadata, ticketId, rawSpecification, version)` | `TicketAssessmentResponse(refinedSpecification, recommendedChangeType, version)` | 기획 적합성·공수·change type 평가 |
| `planImplementation` | `PlanningRequest(metadata, refinedSpecification, version)` | `PlanningResponse(implementationPlanRef, attemptPolicy, version)` | 구현계획·성공/테스트 기준 생성 |
| `prepareWorkspace` | `WorkspaceRequest(metadata, changeType, featureSlug, version)` | `WorkspaceResponse(workspaceRef, branchName, version)` | 브랜치/worktree 확보 (Workflow Run당 1회) |
| `implement` | `ImplementationRequest(metadata, workspaceRef, implementationPlanRef, version)` | `ImplementationResponse(implementationArtifactRef, version)` | Agent Adapter로 구현 실행 |
| `runQualityAssurance` | `QaRequest(metadata, workspaceRef, implementationArtifactRef, version)` | `QaResult(passed, score, reportRef, version)` | QA 실행, 점수·리포트 산출 |
| `recordAttemptHistory` | `AttemptHistoryRequest(metadata, implementationArtifactRef, qaReportRef, qaScore, status, version)` | `AttemptHistoryResponse(recorded, version)` | Attempt 산출물·QA 결과·상태를 불변 이력으로 저장 |
| `manageSourceControl` | `SourceControlRequest(metadata, workspaceRef, action, version)` | `SourceControlResponse(prUrl, status, version)` | Draft PR 생성/상태 조회/승인된 병합 |
| `sendNotification` | `NotificationRequest(metadata, channel, message, version)` | `NotificationResponse(delivered, version)` | 승인 대기·실패·완료 알림 |

## JSON 예시

```json
{
  "metadata": {
    "workflowRunId": "run-42",
    "stage": "QA",
    "attemptNumber": 1,
    "version": 1
  },
  "workspaceRef": { "value": "workspace://run-42", "version": 1 },
  "implementationArtifactRef": { "value": "artifact://run-42/impl-1", "kind": "IMPLEMENTATION_SUMMARY", "version": 1 },
  "version": 1
}
```

위는 `QaRequest`의 직렬화 예시다. Jackson은 Java record를 필드명 그대로 JSON 키로 직렬화한다(`ActivityContractSerializationTest`의 round-trip 테스트로 검증).

## 경계 규칙

- `engine` 모듈은 Claude CLI, GitHub/Jira/GitLab API 클라이언트, 파일시스템 접근 타입을 import하지 않는다 — 이 계약(record + `EngineActivities` 인터페이스)만으로 컴파일된다.
- 실제 Activity 구현체(Claude 실행, Git 조작, Ticket Sync 등)는 이 문서의 범위 밖이며, 별도 Agent Adapter/Ticket Sync 스펙에서 `agent-execution`, `quality-assurance`, `source-control`, `ticket-sync` Task Queue를 구독해 구현한다.
