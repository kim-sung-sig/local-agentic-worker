# [Plan] Agent Worker Engine — T04 Six-Stage Temporal Workflow and Gates

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t04 |
| 작성일 | 2026-07-16 |
| 상태 | Plan |
| 의존 | T01(완료 99%), T02(완료 97%), T03(완료 99%) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [task-04-six-stage-workflow.md](../../tasks/agent-worker-engine/task-04-six-stage-workflow.md) |
| 기술 스택 | Java 21 + Temporal Workflow/Signal/Query |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | T01~T03은 배선/상태모델/계약만 준비했을 뿐, 실제로 6단계를 순서대로 실행하고 승인·반려·취소를 받는 Workflow가 없다. |
| **Solution** | `AgentWorkerWorkflow`/`Impl`을 구현해 Intake/Planning/QA/Review-Merge 게이트에서 승인을 기다리고, Workspace/Implementation은 자동 진행하며, 반려 시 지정된 단계로 되돌아가는 durable Workflow를 만든다. |
| **Function/UX Effect** | 이 Workflow가 이후 T07(Source control gate)·T08(API)에서 사용자에게 노출되는 승인/반려 UX의 실행 엔진이 된다. |
| **Core Value** | 스펙의 승인/반려/재시도 정책(Acceptance Criteria 3·4·6·7)을 Temporal의 durable 실행으로 강제한다 — 서버 재시작에도 마지막 기록 단계에서 복구된다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 승인 게이트와 반려 라우팅이 없으면 사람이 통제 가능한 개발 워크플로라는 스펙의 핵심 가치가 실현되지 않는다 |
| **WHO** | T07 Source control gate, T08 API 개발자가 이 Workflow의 Signal/Query를 호출한다 |
| **RISK** | Workflow 구현에 비결정적 코드(Instant.now, Thread, 리포지토리 호출)가 들어가면 replay가 실패하고 durable 보장이 깨진다 |
| **SUCCESS** | 게이트 승인 전에는 다음 단계로 진행할 수 없고, 반려는 사유를 보존한 채 지정된 단계로 돌아가며, replay가 동일한 순서의 Activity 호출을 만든다 |
| **SCOPE** | Workflow/Signal/Query 정의와 단일 세션 내 게이트 로직만 — 실제 Activity 구현체는 범위 밖(T03에서 계약만 정의됨) |

---

## 1. Problem Statement

### 1-1. 목표 상태 (T04 완료 이후)

```
AgentWorkerWorkflow.run(StartAgentWorkflowRequest)
  INTAKE(게이트) → PLANNING(게이트) → WORKSPACE(자동) → IMPLEMENTATION(자동)
    → QA(게이트, 반려 시 IMPLEMENTATION 재시도) → REVIEW_MERGE(게이트) → COMPLETED

Signal: approve() / reject(reason, targetStage) / requestRevision(reason) / retryStage() / cancel()
Query: currentStage() / status()
```

---

## 2. Goals / Non-Goals

### Goals

- [ ] `AgentWorkerWorkflow` 인터페이스 — 1개 `@WorkflowMethod` + 5개 Signal + Query 2종
- [ ] `AgentWorkerWorkflowImpl` — 단계 디스패치 루프로 임의 단계 되돌아가기(반려)를 지원
- [ ] `AgentWorkerStarter` — `WorkflowClient`로 Workflow를 시작하는 애플리케이션 서비스
- [ ] 게이트: INTAKE/PLANNING/QA/REVIEW_MERGE만 승인 필요, WORKSPACE/IMPLEMENTATION은 자동
- [ ] 반려는 사유(reason)와 대상 단계(targetStage)를 보존하고 PAUSED 상태로 전환, `retryStage()`로 재개
- [ ] Workflow 코드에 `Thread`/`Instant.now`/파일 I/O/리포지토리 호출 없음 — `Workflow.await`/`Workflow.currentTimeMillis`/Activity만 사용

### Non-Goals

- 실제 Activity 구현체 (Claude 실행, Git, QA 등) — 별도 Agent Adapter 스펙
- Workflow 상태를 T02의 `WorkflowRunRepository`에 동기화하는 프로젝션 — 이후 Task(T07/T08 연계)에서 별도 Activity로 다룸
- 기존 `agent` BC 코드 변경

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | INTAKE/PLANNING/QA/REVIEW_MERGE는 `approve()` 없이 다음 단계로 진행할 수 없다 | High | Pending |
| FR-02 | WORKSPACE/IMPLEMENTATION은 게이트 없이 자동 진행한다 | High | Pending |
| FR-03 | `reject(reason, targetStage)`는 사유를 보존하고 지정된 단계로 되돌아간다 (PAUSED) | High | Pending |
| FR-04 | `retryStage()`는 PAUSED 상태에서 현재 단계를 재실행한다 | High | Pending |
| FR-05 | `cancel()`은 언제든 Workflow를 CANCELLED로 종료한다 | High | Pending |
| FR-06 | Workflow replay가 동일한 순서의 Activity 호출을 만든다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 결정성 | Workflow 코드에 `Thread`/`Instant.now`/I/O 없음 | 코드 리뷰 + replay 테스트 |
| 큐 일관성 | T01의 `agent-worker-engine` 큐, T03의 `EngineActivities` 계약을 그대로 재사용 | 코드 리뷰 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 게이트 승인 전 다음 단계 진행 불가 (테스트로 검증)
- [ ] 반려가 사유를 보존하고 지정된 단계로 돌아감 (테스트로 검증)
- [ ] Replay 테스트가 동일한 Activity 호출 순서를 보인다
- [ ] `./gradlew.bat test --tests "*AgentWorkerWorkflowTest"` 통과
- [ ] `./gradlew.bat check` 통과 (기존 무관 실패 제외)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 임의 단계로의 반려(reject)를 선형 코드로 구현하면 재실행 로직이 꼬인다 | High | Medium | 단계를 `while(currentStage != null)` 디스패치 루프로 구성해 어떤 단계로도 돌아갈 수 있게 설계 |
| Workflow가 우연히 비결정적 API를 호출 | High | Low | 코드 리뷰 체크리스트 + replay 테스트로 즉시 탐지 |
| WORKSPACE 재실행 시 WorkspaceRef 중복 할당 | Medium | Low | T03 계약의 멱등키 설계에 위임 — Activity 구현체가 멱등성 보장 (이번 Task 범위 밖) |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `engine.workflow.AgentWorkerWorkflow(Impl)` | New | 6단계 Workflow 신규 |
| `engine.application.service.AgentWorkerStarter` | New | Workflow 시작 서비스 신규 |

### 6.2 Current Consumers

T01~T03 산출물은 이번 변경을 참조하지 않는다 — `EngineHealthWorkflow`는 무변경, `EngineActivities`/계약 record는 그대로 재사용.

---

## 7. Next Steps

1. [ ] `/pdca design agent-worker-engine-t04`
2. [ ] TDD로 `/pdca do agent-worker-engine-t04`
3. [ ] `/pdca analyze agent-worker-engine-t04` — 90점 미만 시 최대 2회 반복

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
