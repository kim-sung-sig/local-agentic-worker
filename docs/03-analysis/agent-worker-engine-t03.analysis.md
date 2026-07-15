# [Analysis] Agent Worker Engine — T03 Versioned Activity Contracts

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 계약이 없으면 엔진과 Worker 구현체가 결합되어 언어/구현 교체가 불가능하고 Workflow 결정성이 깨질 위험이 있다 |
| **SUCCESS** | 모든 외부 부작용 계약에 멱등키(`{workflowRunId}:{stage}:{attempt}`)가 있고, 큰 산출물은 `ArtifactRef`로만 표현된다 |
| **SCOPE** | DTO 계약 + `EngineActivities` 인터페이스 정의만 |

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

Design §4.1에 선언된 21개 산출물(값 객체 5, Request/Response 15, `EngineActivities` 1) + 테스트 1 + 계약 문서 1 전부 정확한 위치에 존재.

## 기능 (Functional) — 98%

Task-03 성공 기준 4개 전부 충족:

- **a. 구현체 import 없음**: `engine` 모듈 전체에서 `com.github`/`io.github`/`jgit`/`anthropic`/`claude`/`jira`/`atlassian`/`java.io.File`/`java.nio.file` 검색 결과 0건 — 계약 record + `io.temporal.activity.*` + 도메인 `WorkflowStage`만 참조
- **b. 멱등키**: 8개 `*Request` record 모두 `ActivityRequestMetadata metadata`를 첫 필드로 선언
- **c. 대용량 산출물은 ArtifactRef만**: `logContent`/`diffContent`/`rawOutput` 류의 원문 필드 없음. `TicketAssessmentRequest.rawSpecification` 등은 짧은 입력 텍스트라 FR-03 위반 아님(Minor 참고)
- **d. version 필드**: 20개 record 전부 마지막 필드로 `int version` 보유, 리플렉션 테스트로 강제

## 계약 (Contract) — 100%

- `EngineActivities` 8개 메서드가 Design §2.3과 정확히 일치 (`runQualityAssurance`는 별도 Response 없이 `QaResult` 직접 반환 포함)
- `idempotencyKey()` = `workflowRunId + ":" + stage + ":" + attemptNumber` → 정확히 `{workflowRunId}:{stage}:{attemptNumber}` 형식 생성, 테스트로 검증(`"run-42:QA:3"`)
- `docs/contracts/agent-worker-activity-v1.md`의 필드 표/버전 정책/멱등키 규칙/JSON 예시가 코드와 드리프트 없이 일치

## TDD 프로세스 확인

Design §3.1의 4개 시나리오(전체 record round-trip, version 리플렉션, metadata 리플렉션, 멱등키 포맷) 전부 커버 + 보너스로 "모든 계약 타입은 record" 검증까지 추가(task-03 리뷰 항목 "DTO가 record인지 확인"을 자동화).

## Plan Success Criteria (§4.1) 평가

| Criterion | Status | Evidence |
|-----------|:------:|----------|
| `test --tests "*ActivityContractSerializationTest"` 통과 | ✅ Met | 실측 통과 |
| `./gradlew.bat check` 통과 (기존 무관 실패 제외) | ✅ Met | 기존 무관 실패 7건만 존재 |
| 엔진이 구현 타입 없이 컴파일 | ✅ Met | 금지 import 0건 |
| 모든 외부 부작용 계약에 멱등키 존재 | ✅ Met | 8개 Request 전부 metadata 보유 |

**4/4 완전 충족.**

## Gaps

Critical/Important 없음.

**Minor (관찰 사항, 결함 아님)**:
1. `TicketAssessmentRequest.rawSpecification`/`TicketAssessmentResponse.refinedSpecification`/`NotificationRequest.message`는 짧은 텍스트 입력이라 Design과 일치 — 향후 티켓 기획 원문이 커지면 v2에서 `ArtifactRef`화 고려
2. `AttemptHistoryRequest.qaScore`가 `int`가 아닌 nullable `Integer` — Design에 타입 명시 없었고 합리적 선택, 조치 불필요
3. `ActivityRequestMetadata`가 `engine.domain.model.WorkflowStage`를 재사용 — Design §2.1에서 의도된 것이며 Jackson이 문자열로 직렬화하므로 언어 중립성 훼손 없음

## Decision

**Match Rate 99% (>= 90% 기준 충족)** — 반복 개선 불필요. T03 완료로 판단하고 T04(Six-stage workflow)로 진행.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial analysis — Match Rate 99% | Claude |
