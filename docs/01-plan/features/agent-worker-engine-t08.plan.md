# [Plan] Agent Worker Engine — T08 Engine API, Observability, and Integration QA

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t08 |
| 작성일 | 2026-07-16 |
| 상태 | Plan |
| 의존 | T07(완료 100%) — 마지막 Task |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [task-08-api-integration-qa.md](../../tasks/agent-worker-engine/task-08-api-integration-qa.md) |
| 기술 스택 | Spring Web + Temporal Client + JPA + Testcontainers PostgreSQL |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | T01~T07은 엔진의 각 조각(Temporal 배선, 상태 모델, 계약, 6단계 Workflow, Workspace 런타임, QA 루프, SCM 게이트)을 독립적으로 완성했지만, 이들을 실제로 연결해 호출하는 API도 없고 전체 흐름이 실제로 동작하는지 통합 검증도 없다. |
| **Solution** | `WorkflowRunController`로 시작/조회/Attempt 이력/승인·반려 API를 제공하고, T02~T07 산출물을 연결하는 참조용 `EngineActivitiesImpl`을 구현해 Temporal 테스트 환경 + Testcontainers PostgreSQL로 Intake→병합까지 전 구간을 통합 검증한다. |
| **Function/UX Effect** | 최초로 사람이 API를 통해 실제로 Workflow Run을 시작하고, 상태를 조회하고, 승인/반려 결정을 내릴 수 있게 된다. |
| **Core Value** | 스펙의 전체 Acceptance Criteria(1~8)를 하나의 실행 가능한 흐름으로 증명 — 이 Task의 통과가 곧 `agent-worker-engine` 스펙 구현 완료의 증거가 된다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 조각들이 개별적으로 완성되어도 실제로 연결해 끝까지 동작시켜보지 않으면 통합 결함(계약 불일치, 상태 동기화 누락)을 발견할 수 없다 |
| **WHO** | 이 API의 실제 소비자(Ticket Sync, 향후 UI)와, 향후 진짜 AI 기반 Agent Adapter로 `EngineActivitiesImpl`을 교체할 개발자 |
| **RISK** | API 응답에 워크스페이스의 실제 파일시스템 경로나 비밀 정보가 노출되면 보안 사고로 이어진다 |
| **SUCCESS** | API로 모든 Attempt의 산출물·QA 리포트 참조를 조회할 수 있고, 잘못된 단계 결정은 Workflow Run을 변경하지 않고 거부되며, 통합 테스트가 Intake부터 병합 완료까지 재사용된 WorkspaceRef 하나로 실행된다 |
| **SCOPE** | API 계층 + 참조용 Activity 구현체(실제 AI/QA 판단 로직 제외) + 통합 테스트 — 실제 GitHub 인증이나 Ticket Sync 연동은 범위 밖 |

---

## 1. Problem Statement

### 1-1. 목표 상태 (T08 완료 이후)

```
POST   /api/engine/workflow-runs                       → Workflow Run 시작
GET    /api/engine/workflow-runs/{workflowRunId}        → 현재 단계/상태 조회 (Temporal 실시간 Query)
GET    /api/engine/workflow-runs/{workflowRunId}/attempts → Attempt 이력 조회 (T02 PostgreSQL 프로젝션)
POST   /api/engine/workflow-runs/{workflowRunId}/decisions → approve/reject/requestRevision/retry/cancel

EngineActivitiesImpl (참조 구현)
  ├── prepareWorkspace       → T05 GitWorktreeRuntime
  ├── recordAttemptHistory   → T02 WorkflowRunRepository/AttemptRecordRepository
  ├── manageSourceControl    → T07 SourceControlPlugin
  └── assessTicket/planImplementation/implement/runQualityAssurance/sendNotification
        → 결정론적 참조 스텁(실제 AI/QA 판단은 범위 밖, 통합 검증용 최소 구현)
```

---

## 2. Goals / Non-Goals

### Goals

- [ ] Workflow Run 시작/조회/Attempt 이력 조회/단계 결정 API 4종
- [ ] 잘못된 단계 결정(예: 대상 단계 없는 REJECT)은 Signal 전송 전에 거부되어 Workflow Run에 어떤 영향도 주지 않는다
- [ ] API 응답에 파일시스템 경로·비밀 정보 노출 없음(WorkspaceRef는 API에 노출하지 않음)
- [ ] T02/T05/T07 산출물을 실제로 연결하는 `EngineActivitiesImpl` 참조 구현
- [ ] Temporal 테스트 환경 + Testcontainers PostgreSQL + 임시 Git 저장소로 Intake→병합 전 구간 통합 테스트
- [ ] `docs/architecture/system-architecture.md`에 구현 완료 컴포넌트 상태 반영(테스트 통과 후)

### Non-Goals

- 실제 AI 기반 구현/QA 판단 로직 — `EngineActivitiesImpl`의 assessTicket/planImplementation/implement/runQualityAssurance는 통합 검증을 위한 결정론적 스텁이며, 향후 별도 Agent Adapter가 교체한다
- 실제 GitHub 인증 연동 — 통합 테스트는 `SourceControlPlugin`의 테스트 대역(fake)을 사용
- Ticket Sync, 실시간 알림 전송 — 범위 밖

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `POST /workflow-runs`는 새 Workflow Run을 시작하고 워크플로 식별자를 반환한다 | High | Pending |
| FR-02 | `GET /workflow-runs/{id}`는 현재 단계·상태를 실시간으로 조회한다(Temporal Query) | High | Pending |
| FR-03 | `GET /workflow-runs/{id}/attempts`는 모든 Attempt의 산출물/QA 리포트 참조/점수/상태/시각을 반환한다 | High | Pending |
| FR-04 | `POST /workflow-runs/{id}/decisions`는 approve/reject/requestRevision/retry/cancel을 Signal로 변환한다 | High | Pending |
| FR-05 | 유효하지 않은 결정(REJECT without targetStage, REQUEST_REVISION without reason)은 Signal을 보내지 않고 400으로 거부한다 | High | Pending |
| FR-06 | 존재하지 않는 Workflow Run 조회는 404로 매핑된다 | Medium | Pending |
| FR-07 | `EngineActivitiesImpl`이 T02/T05/T07을 실제로 연결해 6단계 전체가 동작한다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 보안 | API 응답에 워크스페이스 파일시스템 경로 노출 없음 | 코드 리뷰 + 응답 스키마 확인 |
| 관측성 | 단계 전이·승인 대기·Attempt 점수·Activity 실패·병합 결과에 구조적 로그 | 코드 리뷰 |
| 테스트 환경 견고성 | Docker 미가용 환경에서 통합 테스트는 실패 대신 스킵 | `Assumptions.assumeTrue` |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] API로 모든 Attempt의 산출물·QA 리포트 참조 조회 가능
- [ ] 잘못된 단계 결정이 Workflow Run을 변경하지 않고 거부됨
- [ ] 통합 테스트가 Intake부터 병합 완료까지 승인과 함께 실행되고 재사용된 WorkspaceRef 하나를 검증
- [ ] `./gradlew.bat test` 통과 (Docker 미가용 시 통합 테스트는 스킵)
- [ ] `./gradlew.bat check` 통과 (기존 무관 실패 제외)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 이 샌드박스 환경에 Docker 데몬이 없어 Testcontainers 통합 테스트를 직접 실행하지 못할 수 있음 | Medium | High | `DockerClientFactory.instance().isDockerAvailable()`로 감지해 스킵 처리, 로컬 Docker 있는 환경에서 재실행 권장(T02/T05 통합 테스트와 동일한 기존 정책) |
| API 응답이 실수로 워크스페이스 경로를 노출 | High | Low | `WorkspaceRef`를 어떤 API 응답에도 포함하지 않도록 설계로 원천 차단 |
| 참조용 Activity 구현체가 실제 Agent Adapter로 오인됨 | Low | Medium | 클래스/문서에 "참조 구현, 실제 AI 판단 아님"을 명시 |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `engine.api.*` | New | Controller/Request/Response 5종 신규 |
| `engine.infrastructure.activity.EngineActivitiesImpl` | New | T02/T05/T07 연결 참조 구현 |
| `engine.infrastructure.temporal.TemporalConfiguration` | Modify | `EngineActivitiesImpl`을 Worker에 등록 |
| `common.exception.ErrorCode` | Modify | `WORKFLOW_RUN_NOT_FOUND`, `INVALID_STAGE_DECISION` 추가 |
| `build.gradle` | Modify | Testcontainers(JUnit5, PostgreSQL) 테스트 의존성 추가 |
| `docs/architecture/system-architecture.md` | Modify | 구현 완료 컴포넌트 상태 반영 |

### 6.2 Current Consumers

기존 `agent`/`issue`/`project` BC는 무변경. `ErrorCode` enum에 항목을 추가하는 것은 기존 값에 영향 없음(enum switch에 새 case만 추가).

---

## 7. Next Steps

1. [ ] `/pdca design agent-worker-engine-t08`
2. [ ] TDD로 `/pdca do agent-worker-engine-t08`
3. [ ] `/pdca analyze agent-worker-engine-t08` — 90점 미만 시 최대 2회 반복
4. [ ] 전체 8개 Task 완료 보고서 작성

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
