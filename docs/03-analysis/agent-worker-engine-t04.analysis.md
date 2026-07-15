# [Analysis] Agent Worker Engine — T04 Six-Stage Temporal Workflow and Gates

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 승인 게이트와 반려 라우팅이 없으면 사람이 통제 가능한 개발 워크플로라는 스펙의 핵심 가치가 실현되지 않는다 |
| **SUCCESS** | 게이트 승인 전에는 다음 단계로 진행할 수 없고, 반려는 사유를 보존한 채 지정된 단계로 돌아가며, replay가 동일한 순서의 Activity 호출을 만든다 |
| **SCOPE** | Workflow/Signal/Query 정의와 게이트 로직만 |

---

## Match Rate

Static-only 분석 — `Overall = Structural×0.2 + Functional×0.4 + Contract×0.4`

| Axis | Score |
|------|:-----:|
| Structural | 100% |
| Functional | 98% |
| Contract | 100% |
| **Overall Match Rate** | **99%** |

## 구조 (Structural) — 100%

Design §5.1의 6개 산출물(`StartAgentWorkflowRequest`, `AgentWorkerWorkflow`, `AgentWorkerWorkflowImpl`, `AgentWorkerStarter`, 테스트, `TemporalConfiguration` 수정) 전부 정확한 위치에 존재. 테스트의 `EngineActivitiesDelegate` 헬퍼는 Design에 없지만 Mockito가 `@ActivityMethod` 애노테이션을 프록시에 복사해 Temporal 등록을 거부하는 문제의 정당한 해결책으로 범위 이탈 아님.

## 기능 (Functional) — 98%

- **게이트 차단(FR-01/02)**: `awaitGate`가 `Workflow.await(() -> approveSignaled || cancelSignaled || rejectionTarget != null)`로 차단, INTAKE/PLANNING/QA/REVIEW_MERGE만 게이트를 거치고 WORKSPACE/IMPLEMENTATION은 무조건 자동 진행 — Design §3.1과 정확히 일치
- **반려 사유 보존 + PAUSED→retryStage 재개(FR-03/04)**: `reject(reason, targetStage)`가 사유·대상 단계를 저장하고, `awaitGate`가 이를 `StageGate`로 `gateHistory`에 기록 후 PAUSED 전환, `retryStage()` 신호로 재개 — Design §1.2의 즉시 점프가 아닌 PAUSED 경유 방식 그대로 구현
- **결정성**: `Thread`/`Instant.now()`/`Random`/`System.currentTimeMillis`/파일 I/O/리포지토리 import 전무 확인. `Instant`는 `Workflow.currentTimeMillis()` 기반 결정론적 변환에만 사용
- **TDD 중 발견한 race condition 버그와 그 수정**: Design §3.2 의사코드는 게이트 진입 시 `approveSignaled`를 미리 리셋하지만, 실제 구현은 **소비 직후에만** 리셋하도록 의도적으로 다르게 구현 — Activity 호출이 진행 중일 때 조기 도착한 신호가 유실되는 실제 버그를 TDD로 발견해 수정한 것으로, 결함이 아닌 개선. Design §3.2는 이 수정을 반영하도록 갱신 필요(Minor-3)
- **감점 사유**: 반려 사유가 `gateHistory`에 기록되지만 이를 조회하는 Query나 이를 직접 검증하는 테스트 단언이 없음(Minor-1) — 현재는 재실행 동작으로 간접 검증됨

## 계약 (Contract) — 100%

- `AgentWorkerWorkflow` 인터페이스가 Design §3과 정확히 일치(1 WorkflowMethod, 5 SignalMethod, 2 QueryMethod)
- `AgentWorkerStarter`가 T01의 `WorkflowClient` 빈과 `agent.engine.temporal.task-queue` 프로퍼티를 그대로 재사용, 새 큐 이름 도입 없음
- T03의 `EngineActivities`와 계약 record를 무변경 재사용

## TDD 프로세스 확인

Design §4.1의 5개 시나리오(게이트 차단, 순차 승인 완료, QA 반려→retryStage→IMPLEMENTATION 재개, 취소, 완료된 실행의 replay) 전부 테스트로 커버됨.

## Plan Success Criteria (§4.1) 평가

| Criterion | Status | Evidence |
|-----------|:------:|----------|
| 게이트 승인 전 진행 불가 | ✅ Met | `awaitGate`; `gate_blocksProgressUntilApproved` |
| 반려가 사유 보존 + 지정 단계 복귀 | ✅ Met* | `awaitGate`의 reject 분기; 재실행 동작으로 간접 검증(*문자열 자체를 단언하진 않음, Minor-1) |
| Replay 동일 Activity 호출 순서 | ✅ Met | `replay_completedRunReplaysWithoutError` |
| `test --tests "*AgentWorkerWorkflowTest"` 통과 | ✅ Met | 5/5 통과 |
| `./gradlew.bat check` 통과 (기존 무관 실패 제외) | ✅ Met | 기존 무관 실패 7건만 존재 |

**5/5 충족.**

## Gaps

Critical/Important 없음.

**Minor**:
1. 반려 사유(`reason`)가 `gateHistory`에 기록되지만 조회 불가(Query 없음) — Plan SCOPE상 DB 프로젝션은 T07/T08 이후이므로 허용되나, `gateHistory()` Query를 추가하면 기준을 직접 검증 가능해짐
2. task-04 스펙의 "worker restart 후 복구" 테스트가 Design에서 replay 테스트로 대체됨(내구성 대리 검증) — Design에서 의도된 대체이며 구현이 이를 충실히 따름
3. Design §3.2 의사코드가 TDD로 발견한 수정(소비 직후 리셋) 이전 상태로 남아있음 — 문서 갱신 필요

## Decision

**Match Rate 99% (>= 90% 기준 충족)** — 반복 개선 불필요. T04 완료로 판단하고 T05(Workspace runtime)로 진행.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial analysis — Match Rate 99% | Claude |
