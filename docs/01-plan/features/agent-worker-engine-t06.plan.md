# [Plan] Agent Worker Engine — T06 Implementation, QA, and Attempt History Loop

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t06 |
| 작성일 | 2026-07-16 |
| 상태 | Plan |
| 의존 | T05(완료 99%) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [task-06-implementation-qa-loop.md](../../tasks/agent-worker-engine/task-06-implementation-qa-loop.md) |
| 기술 스택 | Java 21 + Temporal Workflow (T04 확장) |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | T04의 QA 단계는 QA를 1회만 실행하고 곧바로 사람의 승인을 기다린다 — QA 점수가 기준 미달일 때 자동으로 Implementation을 재시도하는 정책 기반 루프가 없다. |
| **Solution** | `AttemptPolicyResolver`로 `minimumQaScore`(기본 90)/`maxAttempts`(기본 2, 1~10)를 검증·해석하고, `AgentWorkerWorkflowImpl`의 QA 단계를 점수·시도 횟수 기반 자동 루프로 확장한다. |
| **Function/UX Effect** | 직접 UI 노출 없음 — 사람은 점수가 기준을 충족했거나 시도가 소진된 뒤에만 게이트 승인을 요청받는다(불필요한 승인 요청 감소). |
| **Core Value** | 스펙의 Acceptance Criteria 4("QA 점수가 기준 미만이면 설정된 총 시도 횟수까지 Implementation으로 돌아간다")를 자동화해 사람 개입 없이 재시도 예산 안에서 스스로 개선을 시도한다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 정책 기반 자동 재시도가 없으면 사소한 QA 미달마다 사람이 매번 반려/재시도 신호를 보내야 한다 |
| **WHO** | T07(Source control gate)이 이 루프가 끝난 뒤의 REVIEW_MERGE 단계를 이어받는다 |
| **RISK** | `minimumQaScore`/`maxAttempts` 검증 없이 사용하면 무한 루프나 0회 시도 같은 잘못된 정책이 그대로 실행된다 |
| **SUCCESS** | 기준과 동일한 점수는 통과로 처리되고, 기본 정책은 최대 2회, 티켓 정책은 최대 10회까지 Attempt를 만들며, 재시도가 새 WorkspaceRef를 만들지 않는다 |
| **SCOPE** | Implementation↔QA 자동 루프 + 정책 해석기만 — Activity 구현체 자체는 범위 밖 |

---

## 1. Problem Statement

### 1-1. 목표 상태 (T06 완료 이후)

```
QA 단계 진입
  loop:
    QA 실행 → AttemptHistory 기록(성공/실패 모두)
    score >= minimumQaScore 또는 attemptNumber >= maxAttempts 이면 루프 종료 → 게이트(사람 승인) 대기
    아니면 attemptNumber++ → Implementation 재실행(같은 WorkspaceRef) → 다시 QA
```

---

## 2. Goals / Non-Goals

### Goals

- [ ] `AttemptPolicyResolver` — `minimumQaScore` 기본 90, `maxAttempts` 기본 2, `maxAttempts`는 1~10 범위 밖이면 예외, `minimumQaScore`는 0~100 범위 밖이면 예외
- [ ] `AgentWorkerWorkflowImpl.handleQa`를 정책 기반 자동 루프로 확장 — 사람의 반려 신호 없이도 점수 미달 시 자동으로 Implementation 재시도
- [ ] 모든 Attempt(성공/실패/에러/취소)에 대해 `recordAttemptHistory` Activity를 1회씩 호출
- [ ] 재시도는 T04에서 이미 만든 `workspace`(WorkspaceRef)를 그대로 재사용 — 새로 만들지 않음

### Non-Goals

- 실제 Activity 구현체 — 별도 Agent Adapter 스펙
- 사람의 명시적 `reject`/`retryStage` 경로 제거 — T04의 수동 반려 경로는 자동 루프 소진 이후에도 그대로 유지(대안 경로)
- 기존 `agent`/`runtime` BC 코드 변경

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `minimumQaScore` 기본값 90, 미설정(0 이하) 시 기본값 적용 | High | Pending |
| FR-02 | `maxAttempts` 기본값 2, 미설정(0 이하) 시 기본값 적용, 1~10 범위 밖이면 예외 | High | Pending |
| FR-03 | `minimumQaScore`가 0~100 범위 밖이면 예외 | High | Pending |
| FR-04 | QA 점수가 `minimumQaScore`와 같으면 통과로 처리 | High | Pending |
| FR-05 | 점수 미달 + 시도 남음 → 자동으로 Implementation 재실행 후 QA 재실행 | High | Pending |
| FR-06 | 시도 소진 시 더 이상 자동 재시도하지 않고 게이트로 진행 | High | Pending |
| FR-07 | 모든 Attempt마다 `recordAttemptHistory` 정확히 1회 호출 | High | Pending |
| FR-08 | 재시도가 `prepareWorkspace`를 다시 호출하지 않는다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 결정성 | 루프 조건 계산에 비결정적 코드 없음 | 코드 리뷰 |
| 로그/원문 격리 | Attempt 기록에 원문 로그/diff가 직접 들어가지 않고 `ArtifactRef`만 사용 | 코드 리뷰(기존 T03 계약 재사용으로 자동 충족) |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 기준과 동일한 점수는 통과 처리된다
- [ ] 기본 정책은 최대 2회, 티켓 정책은 최대 10회까지 Attempt를 만든다
- [ ] 모든 Attempt는 구현 산출물·QA 리포트 참조·점수·상태·시각을 가진다
- [ ] 재시도가 새 WorkspaceRef를 만들지 않는다
- [ ] `./gradlew.bat test --tests "*AttemptPolicyResolverTest" --tests "*AgentWorkerWorkflowTest"` 통과
- [ ] `./gradlew.bat check` 통과 (기존 무관 실패 제외)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 루프 종료 조건 실수로 무한 루프 | High | Low | `maxAttempts` 상한 검증 + 루프 내 `attemptNumber` 단조 증가를 테스트로 고정 |
| 재시도 시 WorkspaceRef를 실수로 재계산 | Medium | Low | `handleImplementation`이 기존 `workspace` 필드를 그대로 참조하도록 유지(신규 `prepareWorkspace` 호출 추가 안 함) |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `engine.application.service.AttemptPolicyResolver` | New | 정책 해석기 신규 |
| `engine.workflow.AgentWorkerWorkflowImpl` | Modify | QA 단계를 정책 기반 자동 루프로 확장 |

### 6.2 Current Consumers

T04의 게이트/신호 계약(`AgentWorkerWorkflow` 인터페이스)은 무변경 — 기존 승인/반려/취소 시맨틱 그대로 유지, 그 위에 자동 루프만 추가.

---

## 7. Next Steps

1. [ ] `/pdca design agent-worker-engine-t06`
2. [ ] TDD로 `/pdca do agent-worker-engine-t06`
3. [ ] `/pdca analyze agent-worker-engine-t06` — 90점 미만 시 최대 2회 반복

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
