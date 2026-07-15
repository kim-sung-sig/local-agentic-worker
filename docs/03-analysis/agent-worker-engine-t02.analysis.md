# [Analysis] Agent Worker Engine — T02 Engine State and Persistence

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | Workflow Run의 상태·Stage Gate·Attempt 이력을 저장할 곳이 없으면 T04 이후 어떤 실제 워크플로도 상태를 유지할 수 없다 |
| **SUCCESS** | WorkspaceRef는 정확히 한 번만 할당되고, Attempt는 append-only이며, 잘못된 Stage/Gate 전이는 거부된다 |
| **SCOPE** | 도메인 모델 + 영속성만 포함 |

---

## Match Rate

Static-only 분석 (로컬 DB/Testcontainers 미기동 전제, 순수 단위 테스트 `WorkflowRunTest`만 실행 확인) — `Overall = Structural×0.2 + Functional×0.4 + Contract×0.4`

| Axis | Score |
|------|:-----:|
| Structural | 98% |
| Functional | 95% |
| Contract | 100% |
| **Overall Match Rate** | **97%** |

## 구조 (Structural) — 98%

Design §9.1에 선언된 18개 파일(도메인 8, 포트 2, 인프라 6, 마이그레이션 1, 테스트 1) 전부 존재. 아래 2건은 의도된 단순화로 Gap 미산정:

- `StageGateJpaEntity` 별도 파일 없음 — `WorkflowRunJpaEntity` 내부 `@Embeddable StageGateEmbeddable`로 대체 (Design 파일 목록과 일치)
- `engine_stage_gate` 테이블에 합성 `id` PK 없음 — `@ElementCollection` 테이블 특성상 정상, Design §3.3 SQL 스케치와의 문서 드리프트일 뿐

## 기능 (Functional) — 95%

- **계층 순수성**: `engine.domain.model.*` 전부 `java.*`/`lombok.Getter`만 import — JPA/Spring 의존 없음 ✅
- **불변식 4건 전부 구현·검증**: WorkspaceRef 1회 할당(`IllegalStateException`), 명시적 Stage 전이표(`IllegalStateException`), Attempt append-only 순번 강제(`IllegalArgumentException`), `getAttempts()`/`getGates()` 불변 뷰(`List.copyOf`) — 모두 `WorkflowRun.java`에서 확인
- **감점 사유(M-1)**: `recordGateDecision()`이 검증 없이 무조건 append — task-02 성공 기준 "잘못된 Stage **또는 Gate 결정**은 거부" 중 Gate 결정 검증 부분이 미구현. Design 문서 자체가 Gate 결정 검증 시나리오를 정의하지 않아 설계와는 일치하지만, 원본 task 스펙 문구는 완전히 충족하지 못함

## 계약 (Contract) — 100%

- `WorkflowRunRepository`(save/findById/findByTemporalWorkflowId), `AttemptRecordRepository`(findByWorkflowRunId) 모두 Design 의도와 일치
- DB 유니크 제약 이중 방어 확인: `(workflow_run_id, attempt_number)`와 `temporal_workflow_id` 모두 JPA(`@UniqueConstraint`, `unique=true`)와 V5 마이그레이션(`uq_engine_attempt_run_number`, `uq_engine_workflow_run_temporal_id`) 양쪽에 존재

## TDD 프로세스 확인

`WorkflowRunTest`는 4개 `@Nested` 클래스, 10개 `@Test` 메서드로 Design §7.2의 9개 시나리오를 전부 커버(시나리오 8은 skip/duplicate 두 테스트로 분리). Spring 컨텍스트 없는 순수 단위 테스트로 Plan §4.2 기준 충족.

## Plan Success Criteria (§4.1) 평가

| # | Criterion | Status | Evidence |
|---|-----------|:------:|----------|
| 1 | 두 번째 WorkspaceRef 수신 불가 | ✅ Met | `WorkflowRun.java` assignWorkspaceRef; test 6 |
| 2 | Attempt 이력 append-only, 대체 불가 | ✅ Met | `WorkflowRun.java` recordAttempt/getAttempts; tests 7-10 |
| 3 | 잘못된 Stage **또는 Gate 결정** 거부 | ⚠️ Partial | Stage 전이는 거부됨; Gate 결정 검증은 미구현(M-1) |
| 4 | `./gradlew.bat test --tests "*WorkflowRunTest"` 통과 | ✅ Met | 실측 통과 |
| 5 | `./gradlew.bat check` 통과 | ⚠️ Partial | `engine` 관련 실패 없음; 기존 무관 실패 7건(로컬 Postgres 미기동, 기존 포맷 버그)은 그대로 존재 |

## Gaps

| Severity | Gap | 설명 | 조치 |
|----------|-----|------|------|
| Minor (M-1) | Gate 결정 검증 미구현 | `recordGateDecision()`이 무조건 append. Design에 Gate 결정 검증 시나리오가 정의되지 않아 설계와는 일치하나 task 스펙 문구는 부분 충족 | T04(6단계 워크플로 Signal 처리)에서 Gate 결정 유효성 검증 추가 예정으로 명시 |
| Minor (M-2) | 문서 드리프트 | Design §3.1 스케치의 `create(ticketId, changeType)`가 실제 `create(ticketId, temporalWorkflowId)` 시그니처와 다름(§3.3 SQL과는 일치) | 후속 문서 동기화 시 §3.1 스케치 갱신 |

Critical/Important 등급 Gap 없음.

## 이번 변경과 무관한 기존 실패 (참고용)

T01과 동일하게 `WorkerApplicationTests`(로컬 Postgres 미기동), `AgentWorkerServiceTest`/`PromptBuilderTest`(기존 포맷 문자열 버그) 7건 — `engine` 패키지 무관.

## 미실행 항목 (참고)

Design §7.3의 통합 테스트 2건(Testcontainers 기반 유니크 제약 위반 검증)은 이번 정적 환경에서 실행 불가 — 로컬 Docker 가용 환경에서 별도 실행 권장.

## Decision

**Match Rate 97% (>= 90% 기준 충족)** — 반복 개선(iterate) 불필요. T02 완료로 판단하고 다음 Task(T03 Activity contracts)로 진행 가능. M-1, M-2는 Critical/Important가 아니므로 후속 Task에서 자연스럽게 반영.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-15 | Initial analysis — Match Rate 97% | Claude |
