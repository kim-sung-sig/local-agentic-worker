# [Design] Agent Worker Engine — T02 Engine State and Persistence

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | Workflow Run의 상태·Stage Gate·Attempt 이력을 저장할 곳이 없으면 T04 이후 어떤 실제 워크플로도 상태를 유지할 수 없다 |
| **RISK** | WorkspaceRef 재할당이나 Attempt 이력 덮어쓰기를 허용하면 스펙의 Acceptance Criteria 1·2·8을 위반한다 |
| **SUCCESS** | WorkspaceRef는 정확히 한 번만 할당되고, Attempt는 append-only이며, 잘못된 Stage/Gate 전이는 거부된다 |
| **SCOPE** | 도메인 모델 + 영속성만 포함 |

---

## 1. Overview

### 1.1 Design Goals

- 6단계 Stage를 명시적 전이 규칙으로 모델링하고, 잘못된 전이는 도메인 계층에서 예외로 거부한다.
- `WorkspaceRef`는 `WorkflowRun` 생성 후 정확히 한 번만 할당 가능하게 한다 (재할당 시도 시 `IllegalStateException`).
- `AttemptRecord`는 append-only 컬렉션으로 관리하고, 외부에는 불변 뷰만 노출한다.
- DB 유니크 제약을 이중 방어선으로 추가해 동시 요청/버그로 인한 무결성 위반을 차단한다.

### 1.2 Design Principles

- **기존 컨벤션 준수**: `AgentJob`처럼 record 기반 ID, `@Getter` + private 생성자 + 정적 팩토리(`create`/`reconstitute`) 패턴을 그대로 따른다.
- **계층 순수성**: `engine.domain.model`은 Spring/JPA/Lombok 어노테이션 중 `@Getter`만 사용(기존 `AgentJob`과 동일 수준), 영속성 프레임워크 타입 의존 없음.
- **불변식은 도메인이 1차 방어, DB가 2차 방어**: 이번 Task의 핵심 아키텍처 결정 — Plan §7.2에서 확정.

---

## 2. Architecture Decision (연속 — Plan에서 확정)

Plan 문서 §7.2에서 이미 "도메인 가드 클로즈 + DB 유니크 제약" 하이브리드로 결정했다 (기존 `AgentJob` 컨벤션과의 일관성 + 스펙의 강한 무결성 요구 때문에 별도 3안 비교 없이 확정). 이번 설계는 그 결정을 구체화한다.

### 2.1 Component Diagram

```
engine.domain.model
  ├── WorkflowRun (WorkflowRunId, WorkflowStage, WorkflowRunStatus, WorkspaceRef?, List<AttemptRecord>)
  ├── StageGate (GateDecision 이력)
  └── AttemptRecord (append-only, AttemptStatus)

engine.application.port
  ├── WorkflowRunRepository
  └── AttemptRecordRepository

engine.infrastructure.datasource
  ├── WorkflowRunJpaEntity / WorkflowRunJpaRepository / WorkflowRunRepositoryAdapter
  └── AttemptRecordJpaEntity / AttemptRecordJpaRepository / AttemptRecordRepositoryAdapter
```

### 2.2 Data Flow

```
WorkflowRun.create(ticketId, changeType)
  → INTAKE 상태로 시작, WorkspaceRef 없음, Attempt 없음
  → advanceTo(WORKSPACE) 시 assignWorkspaceRef(ref) 1회 허용
  → recordAttempt(AttemptRecord) 호출마다 새 Attempt append (덮어쓰기 불가)
  → Repository.save(workflowRun) → JPA 엔티티 변환 → DB 저장 (유니크 제약 검증)
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| `WorkflowRun` | `WorkflowStage`, `WorkflowRunStatus`, `AttemptRecord` (domain만) | 상태·이력 캡슐화 |
| `WorkflowRunRepositoryAdapter` | `WorkflowRunJpaRepository`, `WorkflowRunJpaEntity` | 영속화 |
| `AttemptRecordRepositoryAdapter` | `AttemptRecordJpaRepository`, `AttemptRecordJpaEntity` | Attempt 이력 조회/저장 |

---

## 3. Data Model

### 3.1 Domain Model

```java
public record WorkflowRunId(UUID value) { ... }

public enum WorkflowStage {
    INTAKE, PLANNING, WORKSPACE, IMPLEMENTATION, QA, REVIEW_MERGE
}

public enum WorkflowRunStatus { RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

public class WorkflowRun {
    private final WorkflowRunId id;
    private final UUID ticketId;
    private WorkflowStage currentStage;
    private WorkflowRunStatus status;
    private String workspaceRef;          // nullable, 1회만 할당
    private final List<StageGate> gates;  // append-only
    private final List<AttemptRecord> attempts; // append-only

    public void advanceTo(WorkflowStage next) {
        if (!isValidTransition(currentStage, next)) {
            throw new IllegalStateException(
                "Invalid stage transition: " + currentStage + " -> " + next);
        }
        this.currentStage = next;
    }

    public void assignWorkspaceRef(String ref) {
        if (this.workspaceRef != null) {
            throw new IllegalStateException("WorkspaceRef already assigned");
        }
        this.workspaceRef = ref;
    }

    public void recordAttempt(AttemptRecord attempt) {
        int expectedNumber = attempts.size() + 1;
        if (attempt.attemptNumber() != expectedNumber) {
            throw new IllegalArgumentException(
                "Attempt number must be " + expectedNumber + " but was " + attempt.attemptNumber());
        }
        this.attempts.add(attempt);
    }

    public List<AttemptRecord> getAttempts() {
        return List.copyOf(attempts); // 외부에서 리스트 조작으로 append-only 우회 방지
    }
}
```

```java
public enum GateDecision { APPROVE, REJECT, REQUEST_REVISION }

public record StageGate(WorkflowStage stage, GateDecision decision, String reason, Instant decidedAt) {}

public enum AttemptStatus { PASSED, FAILED, ERROR, CANCELLED }

public record AttemptRecord(
    int attemptNumber,
    String implementationArtifactRef,
    String qaReportRef,
    Integer qaScore,
    AttemptStatus status,
    Instant createdAt,
    Instant finishedAt
) {}
```

### 3.2 Stage Transition Table

| From | Valid To |
|------|----------|
| INTAKE | PLANNING |
| PLANNING | WORKSPACE |
| WORKSPACE | IMPLEMENTATION |
| IMPLEMENTATION | QA |
| QA | IMPLEMENTATION (재시도), REVIEW_MERGE |
| REVIEW_MERGE | (터미널 — 전이 없음, `WorkflowRunStatus.COMPLETED`로 마감) |

> 스펙(§Workflow)의 `Paused` 분기는 이번 Task 범위 밖(T04에서 Signal과 함께 처리) — `WorkflowRunStatus.PAUSED`만 상태값으로 예약.

### 3.3 Database Schema (V5__add_engine_workflow.sql)

```sql
CREATE TABLE engine_workflow_run (
    id                  UUID PRIMARY KEY,
    ticket_id           UUID NOT NULL,
    temporal_workflow_id VARCHAR(200) NOT NULL,
    current_stage       VARCHAR(30) NOT NULL,
    status               VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    workspace_ref        VARCHAR(500),
    started_at           TIMESTAMP NOT NULL,
    finished_at           TIMESTAMP,
    CONSTRAINT uq_engine_workflow_run_temporal_id UNIQUE (temporal_workflow_id)
);

CREATE TABLE engine_stage_gate (
    id               UUID PRIMARY KEY,
    workflow_run_id  UUID NOT NULL REFERENCES engine_workflow_run(id),
    stage            VARCHAR(30) NOT NULL,
    decision         VARCHAR(30) NOT NULL,
    reason           TEXT,
    decided_at       TIMESTAMP NOT NULL
);

CREATE TABLE engine_attempt_record (
    id                            UUID PRIMARY KEY,
    workflow_run_id               UUID NOT NULL REFERENCES engine_workflow_run(id),
    attempt_number                INT NOT NULL,
    implementation_artifact_ref   TEXT,
    qa_report_ref                 TEXT,
    qa_score                      INT,
    status                        VARCHAR(30) NOT NULL,
    created_at                    TIMESTAMP NOT NULL,
    finished_at                   TIMESTAMP,
    CONSTRAINT uq_engine_attempt_run_number UNIQUE (workflow_run_id, attempt_number)
);

CREATE INDEX idx_engine_stage_gate_run_id ON engine_stage_gate(workflow_run_id);
CREATE INDEX idx_engine_attempt_record_run_id ON engine_attempt_record(workflow_run_id);
```

---

## 4. API Specification

이번 Task는 외부 노출 API가 없다 (T08 범위).

---

## 5. Error Handling

| 상황 | 처리 |
|------|------|
| 잘못된 Stage 전이 시도 | `IllegalStateException` — 도메인 계층에서 즉시 거부 |
| WorkspaceRef 재할당 시도 | `IllegalStateException` |
| Attempt 번호 불연속/중복 | `IllegalArgumentException` (도메인) + DB 유니크 제약 위반 시 `DataIntegrityViolationException` (영속성 계층, 방어적 이중화) |

---

## 6. Security Considerations

이번 Task는 인증/인가를 다루지 않는다 — 신규 테이블은 기존 `agent_job`과 동일한 DB 접근 경계 내에 있다.

---

## 7. Test Plan (TDD — 테스트 먼저 작성)

### 7.1 Test Scope

| Type | Target | Tool | Phase |
|------|--------|------|-------|
| Unit (Red→Green→Refactor) | `WorkflowRun` 도메인 로직 | JUnit 5 + AssertJ | Do |
| Integration (가능한 경우) | JPA 유니크 제약 2건 | Testcontainers PostgreSQL | Do (로컬 Docker 가용 시) |

### 7.2 Unit Test Scenarios (WorkflowRunTest)

| # | Test Description | Expected Result |
|---|-------------------|------------------|
| 1 | 생성 시 `INTAKE` 상태, WorkspaceRef 없음 | `currentStage == INTAKE`, `workspaceRef == null` |
| 2 | `INTAKE → PLANNING → WORKSPACE → IMPLEMENTATION → QA → REVIEW_MERGE` 순차 전이 | 각 단계 전이 성공 |
| 3 | `QA → IMPLEMENTATION` 재시도 전이 | 성공 |
| 4 | `INTAKE → IMPLEMENTATION` 등 건너뛰는 전이 | `IllegalStateException` |
| 5 | `assignWorkspaceRef` 최초 1회 | 성공, `workspaceRef` 설정됨 |
| 6 | `assignWorkspaceRef` 2회째 호출 | `IllegalStateException` |
| 7 | `recordAttempt`로 attempt 1, 2 순차 기록 | `getAttempts().size() == 2`, 순서 보존 |
| 8 | attempt 번호를 건너뛰거나 중복 기록 시도 | `IllegalArgumentException` |
| 9 | `getAttempts()` 반환값을 수정 시도 | `UnsupportedOperationException` (불변 리스트) |

### 7.3 Integration Test Scenarios (선택 — Testcontainers 가용 시)

| # | Test Description | Expected Result |
|---|-------------------|------------------|
| 1 | 동일 `(workflow_run_id, attempt_number)`로 2건 저장 시도 | DB 유니크 제약 위반 예외 |
| 2 | 동일 `temporal_workflow_id`로 2개 WorkflowRun 저장 시도 | DB 유니크 제약 위반 예외 |

### 7.4 Seed Data Requirements

해당 없음 — 도메인 단위 테스트는 seed 불필요.

---

## 8. Clean Architecture

### 8.1 Layer Structure (engine bounded context, 확장)

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Domain** | Stage 전이·WorkspaceRef·Attempt 불변식 | `engine.domain.model` |
| **Application** | Repository 포트 | `engine.application.port` |
| **Infrastructure** | JPA 엔티티/어댑터 | `engine.infrastructure.datasource` |

### 8.2 Dependency Rules

- `engine.domain.model`은 `jakarta.persistence.*`, `org.springframework.*`를 import하지 않는다.
- `engine.infrastructure.datasource`만 domain 클래스를 JPA 엔티티로 변환한다 (`from`/`toDomain` 패턴, 기존 `AgentJobJpaEntity`와 동일).
- `engine.workflow`(T01 산출물)는 이번 Task의 domain/persistence를 참조하지 않는다 (T04에서 연결).

---

## 9. Implementation Guide

### 9.1 File Structure

```
src/main/java/com/example/worker/engine/
├── domain/model/
│   ├── WorkflowRun.java
│   ├── WorkflowRunId.java
│   ├── WorkflowStage.java
│   ├── WorkflowRunStatus.java
│   ├── StageGate.java
│   ├── GateDecision.java
│   ├── AttemptRecord.java
│   └── AttemptStatus.java
├── application/port/
│   ├── WorkflowRunRepository.java
│   └── AttemptRecordRepository.java
└── infrastructure/datasource/
    ├── WorkflowRunJpaEntity.java
    ├── WorkflowRunJpaRepository.java
    ├── WorkflowRunRepositoryAdapter.java
    ├── AttemptRecordJpaEntity.java
    ├── AttemptRecordJpaRepository.java
    └── AttemptRecordRepositoryAdapter.java

src/main/resources/db/migration/
└── V5__add_engine_workflow.sql

src/test/java/com/example/worker/engine/domain/model/
└── WorkflowRunTest.java
```

### 9.2 Implementation Order (TDD)

1. [ ] `WorkflowRunTest` 작성 — §7.2의 9개 시나리오를 모두 실패 테스트로 먼저 작성 (Red)
2. [ ] `WorkflowStage`, `WorkflowRunStatus`, `GateDecision`, `AttemptStatus` enum 정의
3. [ ] `AttemptRecord`, `StageGate` record 정의
4. [ ] `WorkflowRunId` record 정의
5. [ ] `WorkflowRun` 도메인 클래스 구현 — 테스트를 Green으로 전환
6. [ ] 리팩터 — 가드 클로즈 중복 제거, 전이 테이블 명시화 (Refactor)
7. [ ] `WorkflowRunRepository`/`AttemptRecordRepository` 포트 정의
8. [ ] JPA 엔티티 + 어댑터 구현 (`AgentJobJpaEntity` 패턴 그대로)
9. [ ] `V5__add_engine_workflow.sql` 작성 — 3개 테이블 + 유니크 제약 2건

### 9.3 Session Guide

| Session | Phase | Scope |
|---------|-------|-------|
| Session 1 | Do (TDD) | 도메인 모델 전체 (파일 8개) — 테스트 우선 |
| Session 1 (연속) | Do | 영속성 계층 (파일 6개) + 마이그레이션 |

단일 세션으로 완결 가능한 규모 — 모듈 분할 불필요.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-15 | Initial draft | Claude |
