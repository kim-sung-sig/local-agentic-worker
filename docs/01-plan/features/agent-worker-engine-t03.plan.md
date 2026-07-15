# [Plan] Agent Worker Engine — T03 Versioned Activity Contracts

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t03 |
| 작성일 | 2026-07-16 |
| 상태 | Plan |
| 의존 | T01(완료, 99%), T02(완료, 97%) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [task-03-activity-contracts.md](../../tasks/agent-worker-engine/task-03-activity-contracts.md) |
| 기술 스택 | Java 21 record + Jackson (Spring Boot Web 기본 포함) |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | 엔진이 실제 Git/Claude/Jira 구현과 직접 결합되면 언어·구현체 교체가 불가능해지고, Temporal Workflow 안에서 비결정적 I/O를 호출하게 될 위험이 커진다. |
| **Solution** | `engine.application.contract.v1`에 버전 필드를 가진 언어 중립 DTO(record)와 `EngineActivities` 인터페이스를 정의해, 엔진과 실행 Worker 사이의 계약을 코드로 고정한다. |
| **Function/UX Effect** | 직접 UI 노출 없음 — T04(6단계 워크플로)가 이 계약으로 Activity를 호출하고, 이후 별도 Agent Adapter/Ticket Sync 구현체가 이 계약을 구현해 붙는다. |
| **Core Value** | "엔진은 Claude/GitHub/Jira/파일시스템 구현체를 import하지 않고 컴파일된다"는 스펙의 핵심 경계(Agent and Plugin Boundary)를 코드로 강제한다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 계약이 없으면 엔진과 Worker 구현체가 결합되어 언어/구현 교체가 불가능하고 Workflow 결정성이 깨질 위험이 있다 |
| **WHO** | T04(6단계 워크플로)와 향후 별도 Agent Adapter/Ticket Sync 구현체 개발자 |
| **RISK** | `version` 필드나 `ActivityRequestMetadata`(workflowRunId/stage/attempt) 없이 계약이 확장되면 이후 하위 호환이 깨지고 재시도 멱등성이 보장되지 않는다 |
| **SUCCESS** | 모든 외부 부작용 계약에 멱등키(`{workflowRunId}:{stage}:{attempt}`)가 있고, 큰 산출물은 `ArtifactRef`로만 표현된다 |
| **SCOPE** | DTO 계약 + `EngineActivities` 인터페이스 정의만 — 실제 Activity 구현체는 범위 밖 |

---

## 1. Problem Statement

### 1-1. 현재 상태

T02까지는 엔진 내부 상태(`WorkflowRun` 등)만 존재하고, 외부 Worker(Claude 실행, Git, Ticket Sync 등)와 통신할 계약이 없다.

### 1-2. 목표 상태 (T03 완료 이후)

```
EngineActivities (Temporal @ActivityInterface)
  ├── assessTicket(TicketAssessmentRequest) -> TicketAssessmentResponse
  ├── planImplementation(PlanningRequest) -> PlanningResponse
  ├── prepareWorkspace(WorkspaceRequest) -> WorkspaceResponse
  ├── implement(ImplementationRequest) -> ImplementationResponse
  ├── runQualityAssurance(QaRequest) -> QaResult
  ├── recordAttemptHistory(AttemptHistoryRequest) -> AttemptHistoryResponse
  ├── manageSourceControl(SourceControlRequest) -> SourceControlResponse
  └── sendNotification(NotificationRequest) -> NotificationResponse

모든 Request는 ActivityRequestMetadata(workflowRunId, stage, attemptNumber, version)를 포함 → idempotencyKey() 제공
```

---

## 2. Goals / Non-Goals

### Goals

- [ ] `ActivityRequestMetadata`, `WorkspaceRef`, `ArtifactRef`, `AttemptPolicy`, `QaResult` record 정의 (모두 `version` 필드 포함)
- [ ] 8개 Activity 메서드를 가진 `EngineActivities` 인터페이스 정의
- [ ] 외부 상태를 변경하는 모든 Request에 `ActivityRequestMetadata metadata` 포함
- [ ] Jackson 왕복(round-trip) 직렬화 테스트 — v1 패키지의 모든 record 대상
- [ ] 리플렉션 테스트 — `version` 필드 누락 또는 mutating Request에 `metadata` 누락 시 실패
- [ ] `docs/contracts/agent-worker-activity-v1.md` — JSON 필드명, 필수 필드, 멱등키 규칙 문서화

### Non-Goals

- 실제 Activity 구현체(Claude 실행, Git, Ticket Sync 등) — 별도 Agent Adapter/Ticket Sync 스펙
- Temporal Workflow와의 실제 연결(Signal 처리) — T04
- 기존 `agent` BC 코드 변경

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 모든 v1 계약 record는 `version` 필드를 가진다 | High | Pending |
| FR-02 | 외부 상태를 변경할 수 있는 모든 Request는 `workflowRunId`, `stage`, `attemptNumber`(=`ActivityRequestMetadata`)를 포함한다 | High | Pending |
| FR-03 | 대용량 산출물(로그/diff/리포트 원문)은 `ArtifactRef`로만 표현하고 원문을 직접 담지 않는다 | High | Pending |
| FR-04 | 엔진 모듈은 Claude/GitHub/Jira/파일시스템 구현 타입을 import하지 않고 컴파일된다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 직렬화 안정성 | 모든 v1 record가 Jackson으로 왕복 직렬화된다 | 단위 테스트 |
| 계약 규율 | `version`/`metadata` 누락 record는 리플렉션 테스트로 즉시 탐지 | 단위 테스트 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] `./gradlew.bat test --tests "*ActivityContractSerializationTest"` 통과
- [ ] `./gradlew.bat check` 통과 (기존 무관 실패 제외)
- [ ] 엔진 모듈이 Claude/GitHub/Jira/파일시스템 구현 타입 없이 컴파일된다
- [ ] 모든 외부 부작용 계약에 멱등키가 존재한다

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| record 필드 실수로 `version` 누락 | Medium | Medium | 리플렉션 테스트로 컴파일 후 즉시 탐지 |
| Jackson이 Java record를 완전히 지원하지 못하는 구버전 사용 | Low | Low | Spring Boot 3.5의 Jackson 2.15+는 record 네이티브 지원 확인됨 |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `engine.application.contract.v1.*` | New | DTO record 8~13종 신규 |
| `engine.workflow.EngineActivities` | New | Activity 인터페이스 신규 |
| `docs/contracts/agent-worker-activity-v1.md` | New | 계약 문서 |

### 6.2 Current Consumers

T01/T02 산출물(`EngineHealthWorkflow`, `WorkflowRun` 등)은 이번 변경을 참조하지 않는다 — 독립적인 신규 패키지.

---

## 7. Next Steps

1. [ ] `/pdca design agent-worker-engine-t03`
2. [ ] TDD로 `/pdca do agent-worker-engine-t03`
3. [ ] `/pdca analyze agent-worker-engine-t03` — 90점 미만 시 최대 2회 반복

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
