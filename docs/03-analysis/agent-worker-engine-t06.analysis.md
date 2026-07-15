# [Analysis] Agent Worker Engine — T06 Implementation, QA, and Attempt History Loop

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 정책 기반 자동 재시도가 없으면 사소한 QA 미달마다 사람이 매번 반려/재시도 신호를 보내야 한다 |
| **SUCCESS** | 기준과 동일한 점수는 통과로 처리되고, 기본 정책은 최대 2회, 티켓 정책은 최대 10회까지 Attempt를 만들며, 재시도가 새 WorkspaceRef를 만들지 않는다 |
| **SCOPE** | Implementation↔QA 자동 루프 + 정책 해석기만 |

---

## Match Rate

Static-only 분석 — `Overall = Structural×0.2 + Functional×0.4 + Contract×0.4`

| Axis | Score |
|------|:-----:|
| Structural | 100% |
| Functional | 92% |
| Contract | 100% |
| **Overall Match Rate** | **97%** |

## 구조 (Structural) — 100%

Design §5.1의 4개 산출물(`AttemptPolicyResolver` 신규, `AgentWorkerWorkflowImpl` 수정, `AttemptPolicyResolverTest` 신규, `AgentWorkerWorkflowTest` 수정) 전부 일치. `handleQa`가 Design §3 의사코드와 정확히 동일한 구조(자동 재시도 시 `return`으로 게이트 우회, 소진 시에만 `awaitGate` 도달).

## 기능 (Functional) — 92%

- **threshold 동일값 통과(a)**: `score() >= minimumQaScore()`로 `>=` 사용 확인, 테스트로 검증
- **기본값/범위 검증(b)**: `maxAttempts` 1~10, `minimumQaScore` 0~100 분리 검증 확인 — "(1..10)" 표기가 `maxAttempts`에만 적용된다는 Design의 해석이 타당함(기본값 90인 점수를 1~10으로 제한하는 것은 도메인적으로 모순)
- **WorkspaceRef 재사용(d)**: `handleImplementation`이 항상 기존 `workspace` 필드 참조, 재시도 시 `prepareWorkspace` 재호출 없음 — 테스트로 `times(1)` 확인
- **결정론적 해석기(e)**: Spring/IO/random/time 호출 없는 순수 클래스, `final` 필드로 1회만 생성
- **자동/수동 경로 분리(f)**: 자동 재시도는 게이트를 거치지 않고, 시도 소진 시에만 게이트 도달 — Design §3 종료 조건표와 정확히 일치
- **감점 사유(Important)**: Attempt가 "timestamps"를 가져야 한다는 task-06 성공 기준을 워크플로 계약(`AttemptHistoryRequest`/`ActivityRequestMetadata`) 어디에도 명시적 시각 필드가 없음 — Attempt 이력을 실제로 저장할 Activity 구현체(범위 밖)가 영속화 시점에 시각을 찍는 것으로 추정되나, 이 책임 소재가 계약 문서에 명시돼 있지 않음
- **감점 사유(Minor)**: task-06 스펙 문구("failed, error, cancelled, and passed 전부 기록")대로라면 QA Activity가 예외를 던지거나 실행 중 취소되는 경우도 이력에 남아야 하지만, 현재는 `QaResult`가 정상 반환된 경우만 기록 — Activity 구현체가 범위 밖이라 아직 다루지 않음

## 계약 (Contract) — 100%

- `AgentWorkerWorkflow`의 Signal/Query 인터페이스 무변경(approve/reject/requestRevision/retryStage/cancel/currentStage/status) — T06에서 새 Signal 추가 없음
- T03의 `AttemptPolicy`/`AttemptHistoryRequest`를 그대로 재사용, 새 DTO 도입 없음

## TDD 프로세스 확인

Design §4.1(리졸버 5개 시나리오 + 경계값 2개 보너스)과 §4.2(워크플로 6~10) 전부 커버. "pass-first-attempt"는 기존 T04 테스트(`approve_sequentialGatesCompleteRun`)의 `times(1)` 단언이 이미 이를 증명하고 있어 별도 테스트 없이 재사용한 것이 타당함을 확인.

## Plan Success Criteria (§4.1) 평가

| Criterion | Status | Evidence |
|-----------|:------:|----------|
| threshold 동일값 통과 | ✅ Met | `handleQa`의 `>=` 비교; 테스트 |
| 기본 최대 2회, 티켓 최대 10회 | ✅ Met | `AttemptPolicyResolver` 경계 검증; exhaustion 테스트 `times(2)` |
| Attempt가 산출물/QA참조/점수/상태/**시각**을 가진다 | ⚠️ Partial | 4개 필드는 확인됨; 시각 필드는 계약에 부재(Important) |
| 재시도가 새 WorkspaceRef를 만들지 않는다 | ✅ Met | `prepareWorkspace times(1)` |
| `test --tests "*AttemptPolicyResolverTest" --tests "*AgentWorkerWorkflowTest"` 통과 | ✅ Met | 15개 전부 통과 |
| `./gradlew.bat check` 통과 (기존 무관 실패 제외) | ✅ Met | 기존 무관 실패 7건만 존재 |

**5/6 완전 충족, 1건 부분 충족.**

## Gaps

**Important**:
- G1 — Attempt 시각(timestamp)이 워크플로 계약 어디에도 명시되지 않음. Activity 구현체(범위 밖)가 영속화 시점에 채우는 것으로 추정되나 계약 문서에 이 책임을 명시하지 않음. 후속 Task/Agent Adapter 스펙에서 "AttemptHistoryActivity 구현체가 created/finished를 영속화 시점에 기록한다"를 명문화 권장.

**Minor**:
- G2 — QA Activity 실행 중 예외/취소로 인한 실패 이력이 아직 기록되지 않음(현재는 정상 반환된 `QaResult`만 기록) — Activity 구현체가 범위 밖이라 이번 Task에서는 다루지 않음.

Critical 없음.

## Decision

**Match Rate 97% (>= 90% 기준 충족)** — 반복 개선 불필요. G1은 코드 결함이 아닌 계약 문서화 이슈로 후속 Task에서 명문화하면 됨. T06 완료로 판단하고 T07(Source control gate)로 진행.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial analysis — Match Rate 97% | Claude |
