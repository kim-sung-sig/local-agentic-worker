# [Design] Agent Worker Engine — T03 Versioned Activity Contracts

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 계약이 없으면 엔진과 Worker 구현체가 결합되어 언어/구현 교체가 불가능하고 Workflow 결정성이 깨질 위험이 있다 |
| **SUCCESS** | 모든 외부 부작용 계약에 멱등키(`{workflowRunId}:{stage}:{attempt}`)가 있고, 큰 산출물은 `ArtifactRef`로만 표현된다 |
| **SCOPE** | DTO 계약 + `EngineActivities` 인터페이스 정의만 |

---

## 1. Overview

### 1.1 Design Goals

- 모든 계약 record에 `version` 필드를 두어 하위 호환 관리 기준을 코드로 고정한다.
- 외부 상태를 변경하는 모든 Request는 `ActivityRequestMetadata`(workflowRunId/stage/attemptNumber)를 포함해 멱등키를 유도할 수 있게 한다.
- `EngineActivities`는 8개 책임(assessment/planning/workspace/implementation/QA/attempt history/source control/notification)을 메서드로만 노출하고, 구현 타입은 절대 참조하지 않는다.

### 1.2 Architecture Decision

Task-03 파일 목록이 이미 단일 `EngineActivities.java`(8개 메서드를 가진 하나의 `@ActivityInterface`)로 명시되어 있어, 책임별로 8개 Activity 인터페이스를 나누는 대안은 이번 Task 범위에서 채택하지 않는다(스펙의 파일 구조를 그대로 따름 — 별도 3안 비교 불필요). 각 메서드의 Request/Response record만 책임별로 분리한다.

---

## 2. Contract Records (engine.application.contract.v1)

### 2.1 공통/값 객체

```java
public record ActivityRequestMetadata(
    String workflowRunId, WorkflowStage stage, int attemptNumber, int version) {

    public String idempotencyKey() {
        return workflowRunId + ":" + stage + ":" + attemptNumber;
    }
}

public record WorkspaceRef(String value, int version) {}

public record ArtifactRef(String value, String kind, int version) {}

public record AttemptPolicy(int maxAttempts, int minimumQaScore, int version) {}

public record QaResult(boolean passed, int score, ArtifactRef reportRef, int version) {}
```

> `WorkflowStage`는 T02에서 정의한 `engine.domain.model.WorkflowStage`를 그대로 재사용한다 — 동일 JVM/모듈 내 계약이므로 중복 정의하지 않는다 (Jackson은 enum을 이름 문자열로 직렬화하므로 언어 중립성에 영향 없음).

### 2.2 Activity별 Request/Response

| Activity | Request | Response |
|----------|---------|----------|
| assessTicket | `TicketAssessmentRequest(metadata, ticketId, rawSpecification, version)` | `TicketAssessmentResponse(refinedSpecification, recommendedChangeType, version)` |
| planImplementation | `PlanningRequest(metadata, refinedSpecification, version)` | `PlanningResponse(implementationPlanRef, attemptPolicy, version)` |
| prepareWorkspace | `WorkspaceRequest(metadata, changeType, featureSlug, version)` | `WorkspaceResponse(workspaceRef, branchName, version)` |
| implement | `ImplementationRequest(metadata, workspaceRef, implementationPlanRef, version)` | `ImplementationResponse(implementationArtifactRef, version)` |
| runQualityAssurance | `QaRequest(metadata, workspaceRef, implementationArtifactRef, version)` | `QaResult` (§2.1 — 별도 Response 없이 직접 반환) |
| recordAttemptHistory | `AttemptHistoryRequest(metadata, implementationArtifactRef, qaReportRef, qaScore, status, version)` | `AttemptHistoryResponse(recorded, version)` |
| manageSourceControl | `SourceControlRequest(metadata, workspaceRef, action, version)` | `SourceControlResponse(prUrl, status, version)` |
| sendNotification | `NotificationRequest(metadata, channel, message, version)` | `NotificationResponse(delivered, version)` |

모든 `*Request`는 `ActivityRequestMetadata metadata`를 첫 필드로 포함한다(외부 상태를 변경할 수 있으므로 FR-02 충족). 모든 record(Request/Response/값 객체)는 마지막 필드로 `version`을 포함한다(FR-01).

### 2.3 EngineActivities

```java
@ActivityInterface
public interface EngineActivities {
    @ActivityMethod TicketAssessmentResponse assessTicket(TicketAssessmentRequest request);
    @ActivityMethod PlanningResponse planImplementation(PlanningRequest request);
    @ActivityMethod WorkspaceResponse prepareWorkspace(WorkspaceRequest request);
    @ActivityMethod ImplementationResponse implement(ImplementationRequest request);
    @ActivityMethod QaResult runQualityAssurance(QaRequest request);
    @ActivityMethod AttemptHistoryResponse recordAttemptHistory(AttemptHistoryRequest request);
    @ActivityMethod SourceControlResponse manageSourceControl(SourceControlRequest request);
    @ActivityMethod NotificationResponse sendNotification(NotificationRequest request);
}
```

`engine.workflow` 패키지에 위치(T01의 `EngineHealthWorkflow`와 동일 패키지) — Temporal 계약(Workflow+Activity 인터페이스)을 한 곳에 모은다.

---

## 3. Test Plan (TDD)

### 3.1 ActivityContractSerializationTest

| # | Test | Expected |
|---|------|----------|
| 1 | 각 v1 record를 Jackson `ObjectMapper`로 직렬화 후 역직렬화 | 원본과 동일한 값으로 복원 (round-trip) |
| 2 | v1 패키지의 모든 record가 `version` 컴포넌트를 가진다 (리플렉션) | 하나라도 없으면 실패 |
| 3 | 이름이 `Request`로 끝나는 모든 record가 `ActivityRequestMetadata metadata` 컴포넌트를 가진다 (리플렉션) | 하나라도 없으면 실패 |
| 4 | `ActivityRequestMetadata.idempotencyKey()`가 `{workflowRunId}:{stage}:{attempt}` 형식을 반환한다 | 문자열 형식 검증 |

### 3.2 컴파일 경계 검증

`engine` 모듈 전체에 `com.github.*`(GitHub API), Claude CLI, Jira 클라이언트 관련 import가 없음을 코드 리뷰로 확인(자동화된 정적 검사는 범위 밖, Success Criteria는 "컴파일된다"이며 애초에 그런 의존성을 추가하지 않는 것으로 충족).

---

## 4. Implementation Guide

### 4.1 File Structure

```
src/main/java/com/example/worker/engine/
├── application/contract/v1/
│   ├── ActivityRequestMetadata.java
│   ├── WorkspaceRef.java
│   ├── ArtifactRef.java
│   ├── AttemptPolicy.java
│   ├── QaResult.java
│   ├── TicketAssessmentRequest.java / TicketAssessmentResponse.java
│   ├── PlanningRequest.java / PlanningResponse.java
│   ├── WorkspaceRequest.java / WorkspaceResponse.java
│   ├── ImplementationRequest.java / ImplementationResponse.java
│   ├── QaRequest.java
│   ├── AttemptHistoryRequest.java / AttemptHistoryResponse.java
│   ├── SourceControlRequest.java / SourceControlResponse.java
│   └── NotificationRequest.java / NotificationResponse.java
└── workflow/
    └── EngineActivities.java

src/test/java/com/example/worker/engine/application/contract/v1/
└── ActivityContractSerializationTest.java

docs/contracts/
└── agent-worker-activity-v1.md
```

### 4.2 Implementation Order (TDD)

1. [ ] `ActivityContractSerializationTest` 작성 — round-trip + 리플렉션 시나리오 먼저 (Red)
2. [ ] 값 객체 5종 record 정의
3. [ ] Activity별 Request/Response record 정의 (14종)
4. [ ] `EngineActivities` 인터페이스 정의
5. [ ] 테스트 Green 확인
6. [ ] `docs/contracts/agent-worker-activity-v1.md` 작성

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
