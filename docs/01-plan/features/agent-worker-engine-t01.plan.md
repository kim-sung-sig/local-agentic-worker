# [Plan] Agent Worker Engine — T01 Temporal Foundation

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t01 |
| 작성일 | 2026-07-15 |
| 상태 | Plan |
| 의존 | 없음 (agent-worker-engine 8-task 시퀀스의 첫 번째 Task) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [docs/tasks/agent-worker-engine/task-01-temporal-foundation.md](../../tasks/agent-worker-engine/task-01-temporal-foundation.md) |
| 기술 스택 | Java 21 + Spring Boot 3.5.12 + Temporal Java SDK |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | Agent Worker Engine의 6단계 워크플로(Intake→Planning→Workspace→Implementation→QA→Review/Merge)를 구동할 durable 오케스트레이션 런타임이 없다. Temporal 없이는 재시작 복구, Signal 기반 승인/반려, Stage Gate를 구현할 수 없다. |
| **Solution** | Spring Boot에 Temporal Java SDK를 연동하고, 결정론적 규칙만 지키는 최소 `EngineHealthWorkflow`로 Worker/Client 배선이 올바른지 검증한다. |
| **Function UX Effect** | 개발자에게 직접 노출되는 UX는 없다 — 이후 T02~T08이 이 위에서 실제 워크플로를 쌓을 수 있는 기반만 마련한다. |
| **Core Value** | 이후 모든 Engine Task가 의존하는 Temporal 배선을 가장 먼저, 가장 작은 위험으로 검증한다. 기존 `agent` 코드 경로는 전혀 건드리지 않는다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 6단계 durable 워크플로를 구동할 Temporal 기반이 없으면 T02 이후 어떤 Task도 시작할 수 없다 |
| **WHO** | agent-worker-engine을 이어서 구현할 개발자(에이전트) 자신 — 다음 세션에서 T02 Engine state를 이 기반 위에 쌓는다 |
| **RISK** | Temporal SDK 버전을 느슨하게(`1.+`) 고정하면 이후 Task에서 breaking change로 재작업 발생 |
| **SUCCESS** | `EngineHealthWorkflow.run()`이 Temporal을 통해 `"ok"`를 반환하고, Workflow 구현체에 비결정적 I/O가 전혀 없다 |
| **SCOPE** | 이번 Task는 T01만 포함 — 실제 Engine 상태 저장(T02), Activity 계약(T03) 등은 범위 밖 |

---

## 1. Problem Statement

### 1-1. 현재 상태

`engine` bounded context가 아직 존재하지 않는다. `agent` BC(`GitBranchService`, `ClaudeAgentExecutor` 등)는 Temporal 없이 즉시 실행 방식으로 동작 중이며 이 Task 이후에도 그대로 유지된다.

### 1-2. 목표 상태 (T01 완료 이후)

```
Spring Boot 기동
  → TemporalConfiguration이 WorkflowClient + WorkerFactory 빈 생성
  → agent-worker-engine 큐에 EngineHealthWorkflowImpl 등록
  → EngineHealthWorkflow.run() 호출 시 Temporal을 통해 "ok" 반환
```

---

## 2. Goals / Non-Goals

### Goals (이번 Task 범위)

- [ ] `build.gradle`에 Temporal SDK(고정 버전) + 필요 시 Spring 연동 의존성 추가
- [ ] `TemporalConfiguration` — WorkflowServiceStubs/WorkflowClient/WorkerFactory 빈 구성, `agent-worker-engine` 큐 등록
- [ ] `EngineHealthWorkflow` / `EngineHealthWorkflowImpl` — 결정론적 최소 워크플로 (`run()` → `"ok"`)
- [ ] `application.properties`에 Kafka와 분리된 `temporal.*` 로컬 개발용 설정 추가
- [ ] `EngineHealthWorkflowTest` — `TestWorkflowEnvironment` 기반 단위 테스트

### Non-Goals (이번 Task 제외)

- Engine 상태 영속화, Stage Gate, Attempt 이력 (T02)
- Activity 계약/DTO (T03)
- 실제 6단계 워크플로 시그널 처리 (T04)
- 기존 `agent` BC의 어떤 코드도 변경하지 않음

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | Spring Boot가 설정된 namespace/queue로 Temporal client와 worker를 생성한다 | High | Pending |
| FR-02 | `EngineHealthWorkflow.run()`이 Temporal을 통해 `"ok"`를 반환한다 | High | Pending |
| FR-03 | Workflow 구현체에는 Git/파일/CLI/모델 호출 등 비결정적 I/O가 없다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 결정성 | Workflow 코드는 현재 시각/난수/외부 I/O 미사용 | 코드 리뷰 |
| 버전 고정 | Temporal SDK가 명시적 버전(예: `x.y.z`), `1.+` 사용 금지 | `build.gradle` 검토 |
| 설정 분리 | `temporal.*` 프로퍼티가 `kafka.*`와 분리 | `application.properties` 검토 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] Spring Boot가 기동 시 Temporal client/worker를 생성한다
- [ ] `EngineHealthWorkflow.run()`이 `"ok"`를 반환한다 (단위 테스트로 검증)
- [ ] Workflow 구현체에 비결정적 코드가 없다
- [ ] `./gradlew.bat test --tests "*EngineHealthWorkflowTest"` 통과
- [ ] `./gradlew.bat check` 통과

### 4.2 Quality Criteria

- [ ] 신규 코드에 대한 단위 테스트 포함
- [ ] Lint/컴파일 경고 없음

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Temporal SDK 버전을 느슨하게 고정 | Medium | Medium | `build.gradle`에 정확한 버전 문자열 명시 |
| 로컬 Temporal 서버 부재로 통합 테스트 불가 | Low | High | 단위 테스트는 `TestWorkflowEnvironment`로 서버 없이 검증; 실 서버 연동은 수동 확인 항목으로 별도 기록 |
| Workflow 구현체에 실수로 비결정적 코드 유입 | High | Low | 코드 리뷰 체크리스트에 "Workflow 내 I/O 금지" 명시 |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `build.gradle` | Config | Temporal SDK 의존성 추가 |
| `application.properties` | Config | `temporal.*` 프로퍼티 추가 |
| `engine` 패키지 | New BC | `TemporalConfiguration`, `EngineHealthWorkflow(Impl)` 신규 생성 |

### 6.2 Current Consumers

기존 `agent`, `issue`, `project` BC는 이 Task의 변경 사항을 참조하지 않는다 — 신규 격리된 `engine` BC이므로 회귀 영향 없음.

### 6.3 Verification

- [x] 기존 `agent` 실행 경로(GitBranchService, ClaudeAgentExecutor, PullRequestService)는 수정하지 않음
- [x] 기존 Kafka 설정과 `temporal.*` 설정이 분리되어 충돌 없음

---

## 7. Architecture Considerations

### 7.1 Project Level Selection

Enterprise 레벨(Java DDD, 계층 분리)을 그대로 따른다 — `engine` bounded context를 `domain/ application/ infrastructure` 계층으로 구성.

### 7.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| Temporal SDK 연동 방식 | 수동 `@Configuration` / `temporal-spring-boot-starter` | Design 단계에서 3안 비교 후 결정 | 프로젝트 기존 관례(수동 `@Configuration` 위주)와의 정합성 검토 필요 |
| Workflow 큐 이름 | 자유 문자열 | `agent-worker-engine` | task-01 명세 고정값 |

---

## 8. Next Steps

1. [ ] `/pdca design agent-worker-engine-t01` — 설계 문서 작성 (아키텍처 3안 비교)
2. [ ] `/pdca do agent-worker-engine-t01` — 구현
3. [ ] `/pdca analyze agent-worker-engine-t01` — Gap 분석, 90점 미만 시 최대 2회 반복 개선

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-15 | Initial draft | Claude |
