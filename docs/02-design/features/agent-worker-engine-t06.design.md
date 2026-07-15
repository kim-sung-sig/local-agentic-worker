# [Design] Agent Worker Engine — T06 Implementation, QA, and Attempt History Loop

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 정책 기반 자동 재시도가 없으면 사소한 QA 미달마다 사람이 매번 반려/재시도 신호를 보내야 한다 |
| **SUCCESS** | 기준과 동일한 점수는 통과로 처리되고, 기본 정책은 최대 2회, 티켓 정책은 최대 10회까지 Attempt를 만들며, 재시도가 새 WorkspaceRef를 만들지 않는다 |
| **SCOPE** | Implementation↔QA 자동 루프 + 정책 해석기만 |

---

## 1. Overview

### 1.1 Design Goals

- `PlanningResponse.attemptPolicy()`로 전달되는 원시 `AttemptPolicy`(T03 계약, 이미 `planImplementation` Activity 응답에 포함)를 `AttemptPolicyResolver`로 검증·기본값 적용한다.
- QA 게이트(사람 승인)는 유지하되, 그 앞단에 "점수/시도 횟수 기반 자동 재시도" 하위 루프를 추가한다.
- 재시도는 항상 이미 획득한 `workspace` 필드를 재사용 — `prepareWorkspace`를 다시 호출하지 않는다.

### 1.2 Architecture Decision — 정책 해석 위치

`AttemptPolicy`의 원시값은 primitive `int`라 "미설정"을 표현할 별도 값이 없다 — `maxAttempts`/`minimumQaScore`가 0 이하이면 "미설정"으로 간주해 기본값(2, 90)을 적용하고, 그 외 범위 위반(`maxAttempts` 1~10 밖, `minimumQaScore` 0~100 밖)은 예외로 거부한다. `minimumQaScore`의 기본값(90)이 스펙의 "(1..10)" 표기와 맞지 않으므로, 1~10 범위 제약은 `maxAttempts`에만 적용하고 `minimumQaScore`는 점수 스케일에 맞는 0~100으로 별도 검증한다(스펙 문서의 "Ticket 정책이 maxAttempts(1..10)와 minimumQaScore를 오버라이드"라는 문구에서 `(1..10)`이 `maxAttempts`에만 걸리는 것으로 해석 — 별도 3안 비교 불필요, 도메인 상식적으로 유일한 합리적 해석).

`AttemptPolicyResolver`는 Spring 빈이 아닌 순수 클래스로 만든다 — Temporal Workflow 코드(`AgentWorkerWorkflowImpl`)에서 직접 `new`로 사용해야 하므로(Workflow는 Spring 빈을 주입받지 않음), 결정론적 순수 로직만 포함한다.

---

## 2. AttemptPolicyResolver

```java
public class AttemptPolicyResolver {

    public static final int DEFAULT_MAX_ATTEMPTS = 2;
    public static final int DEFAULT_MINIMUM_QA_SCORE = 90;

    public AttemptPolicy resolve(AttemptPolicy raw) {
        int maxAttempts = raw.maxAttempts() <= 0 ? DEFAULT_MAX_ATTEMPTS : raw.maxAttempts();
        int minimumQaScore = raw.minimumQaScore() <= 0 ? DEFAULT_MINIMUM_QA_SCORE : raw.minimumQaScore();

        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 10 but was " + maxAttempts);
        }
        if (minimumQaScore < 0 || minimumQaScore > 100) {
            throw new IllegalArgumentException("minimumQaScore must be between 0 and 100 but was " + minimumQaScore);
        }

        return new AttemptPolicy(maxAttempts, minimumQaScore, raw.version());
    }
}
```

## 3. Workflow QA 루프 확장

`handleQa`를 아래와 같이 확장한다(T04의 게이트 메커니즘은 그대로 재사용):

```java
private void handleQa(StartAgentWorkflowRequest request) {
    if (attemptPolicy == null) {
        attemptPolicy = attemptPolicyResolver.resolve(planning.attemptPolicy());
    }

    QaResult qaResult = activities.runQualityAssurance(new QaRequest(
            metadata(request, WorkflowStage.QA), workspace.workspaceRef(),
            implementation.implementationArtifactRef(), 1));

    activities.recordAttemptHistory(new AttemptHistoryRequest(
            metadata(request, WorkflowStage.QA), implementation.implementationArtifactRef(),
            qaResult.reportRef(), qaResult.score(), qaResult.passed() ? "PASSED" : "FAILED", 1));

    boolean thresholdMet = qaResult.score() >= attemptPolicy.minimumQaScore();
    boolean attemptsRemain = attemptNumber < attemptPolicy.maxAttempts();

    if (!thresholdMet && attemptsRemain) {
        attemptNumber++;
        currentStage = WorkflowStage.IMPLEMENTATION; // 자동 재시도 — 게이트 없이 곧바로 재실행
        return;
    }

    if (awaitGate(WorkflowStage.QA)) {
        currentStage = WorkflowStage.REVIEW_MERGE;
    } else if (status == WorkflowRunStatus.RUNNING) {
        attemptNumber++; // 사람이 명시적으로 반려 → Implementation 재시도(기존 T04 경로, 자동 루프 소진 후 대안)
    }
}
```

`handleImplementation`은 변경하지 않는다 — 이미 `workspace` 필드(최초 1회 획득)를 그대로 참조하므로 재시도 시 `prepareWorkspace`를 다시 호출하지 않는다(FR-08은 코드 변경 없이 기존 구조로 자동 충족).

### 3.1 종료 조건 표

| 조건 | 동작 |
|------|------|
| `score >= minimumQaScore` | 자동 루프 종료 → 게이트 대기 (통과, 사람 승인 시 REVIEW_MERGE) |
| `score < minimumQaScore` 이고 `attemptNumber < maxAttempts` | 자동으로 IMPLEMENTATION 재실행 (게이트 없음) |
| `score < minimumQaScore` 이고 `attemptNumber >= maxAttempts` | 자동 루프 종료 → 게이트 대기 (사람이 승인 시 강제 진행, 반려 시 기존 T04 수동 재시도 경로) |

---

## 4. Test Plan (TDD)

### 4.1 AttemptPolicyResolverTest

| # | Test | Expected |
|---|------|----------|
| 1 | 기본 정책(0,0) | `maxAttempts=2`, `minimumQaScore=90` |
| 2 | 티켓 오버라이드(5, 95) | 그대로 반영 |
| 3 | `maxAttempts=11` | `IllegalArgumentException` |
| 4 | `maxAttempts=0`이 아닌 음수(-1) | 기본값 적용(0 이하는 "미설정"으로 간주 — 음수는 미설정 취급, 별도 거부하지 않음) |
| 5 | `minimumQaScore=101` | `IllegalArgumentException` |

### 4.2 AgentWorkerWorkflowTest 추가 시나리오

| # | Test | Expected |
|---|------|----------|
| 6 | 점수가 threshold와 정확히 같음(threshold equality) | 통과 처리, 재시도 없음 |
| 7 | 첫 Attempt에서 통과(pass-first-attempt) | `implement`/`runQualityAssurance` 각 1회만 호출 |
| 8 | 마지막 Attempt(기본 정책 2회째)에서 통과(pass-last-attempt) | `implement`/`runQualityAssurance` 각 2회 호출, 재시도는 자동(게이트 없이) |
| 9 | 시도 소진(exhaustion) — 2회 모두 미달 | 자동 재시도 없이 게이트로 진행(사람 승인 대기), `implement` 정확히 2회만 호출(3회째 자동 실행 없음) |
| 10 | 매 루프 반복마다 `recordAttemptHistory` 정확히 1회 호출 | Attempt 횟수 == `recordAttemptHistory` 호출 횟수 |

---

## 5. Implementation Guide

### 5.1 File Structure

```
src/main/java/com/example/worker/engine/
├── application/service/
│   └── AttemptPolicyResolver.java
└── workflow/
    └── AgentWorkerWorkflowImpl.java (modify)

src/test/java/com/example/worker/engine/
├── application/service/
│   └── AttemptPolicyResolverTest.java
└── workflow/
    └── AgentWorkerWorkflowTest.java (modify — 시나리오 6~10 추가)
```

### 5.2 Implementation Order (TDD)

1. [ ] `AttemptPolicyResolverTest` 작성 (Red)
2. [ ] `AttemptPolicyResolver` 구현 (Green)
3. [ ] `AgentWorkerWorkflowTest`에 시나리오 6~10 추가 (Red) — mock QA 응답을 시나리오별로 다르게 스텁
4. [ ] `AgentWorkerWorkflowImpl.handleQa` 확장 (Green)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
