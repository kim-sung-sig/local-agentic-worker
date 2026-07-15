# [Design] Agent Worker Engine — T04 Six-Stage Temporal Workflow and Gates

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 승인 게이트와 반려 라우팅이 없으면 사람이 통제 가능한 개발 워크플로라는 스펙의 핵심 가치가 실현되지 않는다 |
| **SUCCESS** | 게이트 승인 전에는 다음 단계로 진행할 수 없고, 반려는 사유를 보존한 채 지정된 단계로 돌아가며, replay가 동일한 순서의 Activity 호출을 만든다 |
| **SCOPE** | Workflow/Signal/Query 정의와 게이트 로직만 |

---

## 1. Overview

### 1.1 Design Goals

- 임의 단계로의 반려(reject)를 지원하기 위해 선형 코드 대신 **단계 디스패치 루프**로 Workflow를 구성한다.
- 게이트가 필요한 4단계(INTAKE/PLANNING/QA/REVIEW_MERGE)와 자동 진행 2단계(WORKSPACE/IMPLEMENTATION)를 하나의 일관된 구조로 표현한다.
- Workflow 상태(현재 단계, 상태, 게이트 이력)는 Workflow 로컬 필드에만 두고, Temporal의 Event History 자체가 durable 저장소 역할을 한다 — 별도 DB 프로젝션 동기화는 이번 Task 범위 밖(T02의 `WorkflowRunRepository`는 이후 Task에서 Activity를 통해 연결).

### 1.2 Architecture Decision — Reject 의미론

스펙의 상태 다이어그램(`Paused --> Intake/Planning/Implementation/QA/ReviewMerge`)을 그대로 따라, `reject`/`requestRevision`은 즉시 이전 단계를 재실행하는 것이 아니라 **PAUSED로 전환**한다. 이후 사용자가 `retryStage()`를 신호하면 그때 지정된 단계(`targetStage`)에서 재개한다. `QA → IMPLEMENTATION` 재시도(스펙의 유일한 자동 루프)도 동일한 PAUSED→retryStage 경로로 통일한다 — 별도의 특수 케이스를 만들지 않는다.

```
Gate stage 도달 → Activity 실행 → 승인 대기
  ├─ approve()        → 다음 단계로 진행
  ├─ reject(reason, targetStage) / requestRevision(reason)
  │     → status=PAUSED, 사유·대상 단계 기록 → retryStage() 대기
  │       retryStage() 신호 시 → status=RUNNING, currentStage=targetStage에서 재개(루프 상단으로)
  └─ cancel()         → status=CANCELLED, 종료
```

### 1.3 Design Principles

- **결정론 준수**: `Workflow.await`, `Workflow.currentTimeMillis()`만 사용, `Thread`/`Instant.now()`/파일 I/O/리포지토리 호출 금지.
- **단계 디스패치 루프**: `while (currentStage != null && status == RUNNING) { switch(currentStage) { ... } }` 구조로 어떤 단계로도 되돌아갈 수 있게 한다.
- **계약 재사용**: T03의 `EngineActivities`와 계약 record를 그대로 사용, 새 DTO를 추가하지 않는다(Workflow 시작 입력 `StartAgentWorkflowRequest`만 예외로 신규 추가).

---

## 2. Component Diagram

```
engine.workflow
  ├── AgentWorkerWorkflow (interface: run + 5 signals + 2 query)
  ├── AgentWorkerWorkflowImpl (단계 디스패치 루프, EngineActivities 스텁 사용)
  └── StartAgentWorkflowRequest (record: workflowRunId, ticketId, rawSpecification, version)

engine.application.service
  └── AgentWorkerStarter (WorkflowClient로 AgentWorkerWorkflow 시작)
```

## 3. Workflow Contract

```java
@WorkflowInterface
public interface AgentWorkerWorkflow {

    @WorkflowMethod
    String run(StartAgentWorkflowRequest request);

    @SignalMethod void approve();
    @SignalMethod void reject(String reason, WorkflowStage targetStage);
    @SignalMethod void requestRevision(String reason);
    @SignalMethod void retryStage();
    @SignalMethod void cancel();

    @QueryMethod WorkflowStage currentStage();
    @QueryMethod WorkflowRunStatus status();
}
```

`WorkflowStage`/`WorkflowRunStatus`는 T02의 `engine.domain.model` enum을 그대로 재사용한다(순수 enum이라 결정론에 안전).

### 3.1 단계별 동작

| Stage | 게이트 필요 | Activity 호출 | 승인 후 다음 단계 |
|-------|:---:|---|---|
| INTAKE | ✅ | `assessTicket` | PLANNING |
| PLANNING | ✅ | `planImplementation` | WORKSPACE |
| WORKSPACE | ❌(자동) | `prepareWorkspace` | IMPLEMENTATION |
| IMPLEMENTATION | ❌(자동) | `implement` | QA |
| QA | ✅ | `runQualityAssurance` + `recordAttemptHistory` | REVIEW_MERGE (반려 시 IMPLEMENTATION로 재개) |
| REVIEW_MERGE | ✅ | `manageSourceControl`(CREATE_DRAFT_PR, 승인 후 MERGE) | 종료(COMPLETED) |

### 3.2 게이트 대기 로직 (의사코드)

```java
private boolean awaitGate(WorkflowStage stage) {
    approveSignaled = false;
    Workflow.await(() -> approveSignaled || cancelSignaled || rejectionTarget != null);

    if (cancelSignaled) { status = CANCELLED; return false; }

    if (rejectionTarget != null) {
        gateHistory.add(new StageGate(stage, lastDecision, lastReason, epochToInstant(Workflow.currentTimeMillis())));
        status = PAUSED;
        WorkflowStage target = rejectionTarget;
        rejectionTarget = null;
        Workflow.await(() -> retryStageSignaled || cancelSignaled);
        if (cancelSignaled) { status = CANCELLED; return false; }
        retryStageSignaled = false;
        status = RUNNING;
        currentStage = target; // 루프 상단에서 target 단계로 재진입
        return false; // 이번 호출은 진행 실패로 처리, 루프가 target에서 다시 시작
    }

    gateHistory.add(new StageGate(stage, GateDecision.APPROVE, null, epochToInstant(Workflow.currentTimeMillis())));
    return true;
}
```

`run()`은 아래처럼 단계 디스패치 루프로 구성한다:

```java
public String run(StartAgentWorkflowRequest request) {
    while (currentStage != null && status == RUNNING) {
        switch (currentStage) {
            case INTAKE -> handleIntake(request);
            case PLANNING -> handlePlanning(request);
            case WORKSPACE -> handleWorkspace(request);
            case IMPLEMENTATION -> handleImplementation(request);
            case QA -> handleQa(request);
            case REVIEW_MERGE -> handleReviewMerge(request);
        }
    }
    return status.name();
}
```

각 `handleX`는 Activity 호출 후, 게이트 단계면 `awaitGate(X)` 결과에 따라 `currentStage`를 다음 단계 또는 그대로(재시도 대기) 유지하고, 자동 단계면 바로 다음 단계로 넘어간다.

---

## 4. Test Plan (TDD)

### 4.1 AgentWorkerWorkflowTest

| # | Test | Expected |
|---|------|----------|
| 1 | Workflow 시작 후 approve 신호 전 | `currentStage() == INTAKE`, `assessTicket` 이후의 Activity(`planImplementation` 등)는 호출되지 않음 |
| 2 | INTAKE→PLANNING→WORKSPACE→IMPLEMENTATION→QA→REVIEW_MERGE까지 순차 approve 신호 | 각 게이트 통과 후 `currentStage()`가 기대한 값으로 전이, 최종 `COMPLETED` 반환 |
| 3 | QA에서 `reject(reason, IMPLEMENTATION)` 신호 후 `retryStage()` | `status()==PAUSED`가 잠시 관측되고, `retryStage()` 후 `currentStage()==IMPLEMENTATION`에서 재개, `implement` Activity가 다시 호출됨, 사유가 게이트 이력에 보존 |
| 4 | 임의 게이트에서 `cancel()` | 최종 반환값이 `CANCELLED`, 이후 Activity가 더 호출되지 않음 |
| 5 | Replay 테스트 | 완료된 Workflow의 History를 `WorkflowReplayer.replayWorkflowExecution`으로 재생해도 예외 없이 통과 |

### 4.2 Mock Activities

`EngineActivities`는 Mockito로 스텁하여 각 메서드가 최소 응답(예: `assessTicket` → `TicketAssessmentResponse("refined", "FEATURE", 1)`)을 반환하도록 구성하고, `Mockito.verify`로 호출 순서/횟수를 검증한다.

---

## 5. Implementation Guide

### 5.1 File Structure

```
src/main/java/com/example/worker/engine/
├── workflow/
│   ├── AgentWorkerWorkflow.java
│   ├── AgentWorkerWorkflowImpl.java
│   └── StartAgentWorkflowRequest.java
└── application/service/
    └── AgentWorkerStarter.java

src/test/java/com/example/worker/engine/workflow/
└── AgentWorkerWorkflowTest.java
```

### 5.2 Implementation Order (TDD)

1. [ ] `StartAgentWorkflowRequest` record 정의
2. [ ] `AgentWorkerWorkflow` 인터페이스 정의
3. [ ] `AgentWorkerWorkflowTest` 작성 — §4.1의 5개 시나리오 먼저 (Red)
4. [ ] `AgentWorkerWorkflowImpl` 구현 — 단계 디스패치 루프 (Green)
5. [ ] `AgentWorkerStarter` 구현 (T01의 `TemporalConfiguration`이 등록한 `WorkflowClient` 재사용)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
