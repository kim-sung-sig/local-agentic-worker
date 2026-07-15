# [Plan] Agent Worker Engine — T02 Engine State and Persistence

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t02 |
| 작성일 | 2026-07-15 |
| 상태 | Plan |
| 의존 | T01 Temporal foundation (완료, Match Rate 99%) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [docs/tasks/agent-worker-engine/task-02-engine-state.md](../../tasks/agent-worker-engine/task-02-engine-state.md) |
| 기술 스택 | Java 21 + Spring Boot 3.5 + JPA + Flyway + PostgreSQL |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | T01은 Temporal 배선만 검증했을 뿐, Workflow Run의 실제 상태(현재 Stage, WorkspaceRef, Stage Gate 결정, Attempt 이력)를 조회하거나 영속화할 방법이 없다. |
| **Solution** | `engine` bounded context에 `WorkflowRun`/`StageGate`/`AttemptRecord` 도메인 모델과 JPA 영속성 어댑터를 추가하여, 6단계 워크플로의 상태와 불변 Attempt 이력을 DB에 저장한다. |
| **Function UX Effect** | 이번 Task는 UI 노출이 없다 — T04(6단계 워크플로)와 T08(API)에서 이 상태를 조회·표시할 수 있는 기반이 된다. |
| **Core Value** | WorkspaceRef 단일 소유권과 Attempt 불변 이력이라는 스펙의 핵심 무결성 규칙(Acceptance Criteria 1, 2, 8)을 도메인 계층에서부터 강제한다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | Workflow Run의 상태·Stage Gate·Attempt 이력을 저장할 곳이 없으면 T04 이후 어떤 실제 워크플로도 상태를 유지할 수 없다 |
| **WHO** | agent-worker-engine을 이어서 구현할 개발자(에이전트) 자신 — T03(Activity 계약), T04(6단계 워크플로)가 이 모델을 사용한다 |
| **RISK** | WorkspaceRef 재할당이나 Attempt 이력 덮어쓰기를 허용하면 스펙의 Acceptance Criteria 1·2·8을 위반한다 |
| **SUCCESS** | WorkspaceRef는 정확히 한 번만 할당되고, Attempt는 append-only이며, 잘못된 Stage/Gate 전이는 거부된다 |
| **SCOPE** | 이번 Task는 도메인 모델 + 영속성만 포함 — Activity 계약(T03), 실제 Temporal 워크플로 연결(T04)은 범위 밖 |

---

## 1. Problem Statement

### 1-1. 현재 상태

`engine` BC에는 T01의 `EngineHealthWorkflow`만 존재하며 영속 상태가 전혀 없다.

### 1-2. 목표 상태 (T02 완료 이후)

```
WorkflowRun (6단계 중 현재 Stage, 단일 WorkspaceRef, Stage Gate 이력)
  ├── StageGate 1..N (단계별 승인/반려/수정 결정 이력)
  └── AttemptRecord 1..N (append-only: attemptNumber, 산출물 참조, QA 점수, status, 시각)

Repository로 저장/조회 가능, DB 유니크 제약으로 이중 방어
```

---

## 2. Goals / Non-Goals

### Goals (이번 Task 범위)

- [ ] `WorkflowRun`/`WorkflowRunId`/`WorkflowStage`/`WorkflowRunStatus` 도메인 모델
- [ ] `StageGate`/`GateDecision`/`AttemptRecord`/`AttemptStatus` 도메인 모델
- [ ] `WorkflowRunRepository`/`AttemptRecordRepository` 포트
- [ ] JPA 엔티티 + 어댑터 (`engine/infrastructure/datasource/*`)
- [ ] `V5__add_engine_workflow.sql` — forward-only 마이그레이션
- [ ] `WorkflowRunTest` — 모든 유효 Stage 전이 + 잘못된 전이 거부 단위 테스트 (TDD)

### Non-Goals (이번 Task 제외)

- Activity 계약/DTO (T03)
- 실제 Temporal Workflow와의 연결, Signal 처리 (T04)
- API 노출 (T08)
- 기존 `agent` BC 코드 변경

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `WorkflowRun`은 6단계(`INTAKE`,`PLANNING`,`WORKSPACE`,`IMPLEMENTATION`,`QA`,`REVIEW_MERGE`)를 순서대로만 전이한다 | High | Pending |
| FR-02 | `WorkflowRun`은 nullable `WorkspaceRef`를 정확히 한 번만 할당할 수 있다 (재할당 시 예외) | High | Pending |
| FR-03 | `AttemptRecord`는 append-only이며 이후 Attempt가 이전 산출물/QA 참조를 덮어쓸 수 없다 | High | Pending |
| FR-04 | 잘못된 Stage 전이 또는 Gate 결정은 도메인 계층에서 예외로 거부된다 | High | Pending |
| FR-05 | `(workflow_run_id, attempt_number)` 및 Temporal workflow ID에 대한 DB 유니크 제약을 추가한다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 계층 순수성 | `engine.domain.model` 패키지는 Spring/JPA import 없음 | 코드 리뷰 |
| 마이그레이션 안전성 | `V5` 마이그레이션은 forward-only, 기존 테이블 무변경 | 코드 리뷰 |
| 동시성 안전 | Attempt 번호 유니크 제약으로 중복 삽입 방지 | DB 제약 + 통합 테스트 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] Workflow Run은 두 번째 WorkspaceRef를 받을 수 없다
- [ ] Attempt 이력은 append-only이며 이후 Attempt가 이전 산출물/QA 참조를 대체할 수 없다
- [ ] 잘못된 Stage 또는 Gate 결정 전이가 거부된다
- [ ] `./gradlew.bat test --tests "*WorkflowRunTest"` 통과
- [ ] `./gradlew.bat check` 통과 (기존 실패 항목 제외)

### 4.2 Quality Criteria

- [ ] TDD로 작성 — 실패하는 테스트 먼저, 이후 구현
- [ ] 도메인 모델 단위 테스트에 Spring 컨텍스트 불필요 (순수 단위 테스트)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 도메인 계층에서만 불변식을 강제하면 동시 요청 시 경쟁 조건으로 위반 가능 | High | Low | DB 유니크 제약(`workflow_run_id`+`attempt_number`, Temporal workflow ID)으로 이중 방어 |
| PostgreSQL Testcontainers 미실행 환경에서 통합 테스트 불가 | Medium | High | 도메인 단위 테스트는 Testcontainers 불필요; JPA 통합 테스트는 로컬 Docker 가용 시에만 실행하고 결과를 Task PR에 별도 기록 |
| Stage 열거형 확장 시 하위 호환 깨짐 | Low | Low | Stage 순서를 열거형 ordinal이 아닌 명시적 전이 테이블로 관리 |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `engine.domain.model.*` | New | WorkflowRun, StageGate, AttemptRecord 등 신규 도메인 모델 |
| `engine.application.port.*` | New | Repository 포트 인터페이스 신규 |
| `engine.infrastructure.datasource.*` | New | JPA 엔티티/어댑터 신규 |
| `src/main/resources/db/migration/V5__add_engine_workflow.sql` | New | 신규 테이블 생성 (forward-only) |

### 6.2 Current Consumers

기존 `agent`/`issue`/`project` BC 및 T01 산출물(`engine.workflow.*`)은 이번 변경을 참조하지 않는다 — 신규 격리된 도메인/영속성 계층이므로 회귀 영향 없음.

### 6.3 Verification

- [x] 기존 `agent_job` 등 기존 테이블/마이그레이션 무변경
- [x] T01 `EngineHealthWorkflow`/`TemporalConfiguration` 무변경

---

## 7. Architecture Considerations

### 7.1 Project Level Selection

Enterprise 레벨(Java DDD, 계층 분리) 유지 — `engine.domain` → `engine.application.port` → `engine.infrastructure.datasource` 순서.

### 7.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| 불변식 강제 위치 | DB 제약만 / 도메인 가드 클로즈만 / 둘 다 | 도메인 가드 클로즈 + DB 유니크 제약(이중 방어) | 기존 `AgentJob` 컨벤션(가드 메서드로 상태 전이 제어)과 일치하며, 스펙의 무결성 요구(Acceptance Criteria 1·2·8)가 강함 |
| Attempt 컬렉션 노출 방식 | mutable List 그대로 반환 / 불변 List 반환 | `List.copyOf()`로 불변 뷰 반환 | 호출자가 리스트를 직접 조작해 append-only 규칙을 우회하지 못하도록 방지 |

---

## 8. Next Steps

1. [ ] `/pdca design agent-worker-engine-t02` — 설계 문서 작성
2. [ ] TDD로 `/pdca do agent-worker-engine-t02` 구현 (실패 테스트 → 구현 → 리팩터)
3. [ ] `/pdca analyze agent-worker-engine-t02` — Gap 분석, 90점 미만 시 최대 2회 반복 개선

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-15 | Initial draft | Claude |
