# [Analysis] Agent Worker Engine — T01 Temporal Foundation

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 6단계 durable 워크플로를 구동할 Temporal 기반이 없으면 T02 이후 어떤 Task도 시작할 수 없다 |
| **SUCCESS** | `EngineHealthWorkflow.run()`이 Temporal을 통해 `"ok"`를 반환하고, Workflow 구현체에 비결정적 I/O가 전혀 없다 |
| **SCOPE** | T01만 포함 |

---

## Match Rate

Static-only 분석 (로컬 Temporal dev server 미기동 전제) — `Overall = Structural×0.2 + Functional×0.4 + Contract×0.4`

| Axis | Score |
|------|:-----:|
| Structural | 100% |
| Functional | 100% |
| Contract | 100% |
| **Overall Match Rate** | **99%** |

(문서 표기 드리프트 1건으로 1점 notional 차감 — 아래 Gap 참조. 3개 축 모두 정식 통과.)

## Plan Success Criteria (§4.1) 평가

| # | Criterion | Status | Evidence |
|---|-----------|:------:|----------|
| 1 | Spring Boot가 기동 시 Temporal client/worker를 생성한다 | ✅ Met | `spring.temporal.*` 프로퍼티로 client 자동구성, `TemporalConfiguration`이 `WorkerFactory`+`Worker`를 생성하고 `SmartLifecycle`로 시작/종료 관리 |
| 2 | `EngineHealthWorkflow.run()`이 `"ok"`를 반환한다 (단위 테스트로 검증) | ✅ Met | `EngineHealthWorkflowTest.run_returnsOk()` 통과 확인 (실측) |
| 3 | Workflow 구현체에 비결정적 코드가 없다 | ✅ Met | `EngineHealthWorkflowImpl.run()`은 `return "ok";` 단일 문 |
| 4 | `./gradlew.bat test --tests "*EngineHealthWorkflowTest"` 통과 | ✅ Met | 실측 통과 (PowerShell 실행, BUILD SUCCESSFUL) |
| 5 | `./gradlew.bat check` 통과 | ⚠️ Partial | `test` 태스크에서 7건 실패하나 전부 이번 변경과 무관한 기존 결함(로컬 Postgres 미기동으로 `WorkerApplicationTests` 실패, `AgentWorkerServiceTest`/`PromptBuilderTest`의 기존 `UnknownFormatConversionException`). `engine` 패키지 관련 실패 없음 |

## Gaps

| Severity | Gap | 설명 | 조치 |
|----------|-----|------|------|
| Minor | 프로퍼티 이름 문서 드리프트 | Design §9.2·task-01 스펙은 `temporal.connection.target` 등을 언급하나, 구현은 공식 `temporal-spring-boot-starter`가 강제하는 `spring.temporal.*` prefix + 별도 `agent.engine.temporal.task-queue`를 사용 — 기능적으로는 올바르고 Kafka 설정과의 분리도 유지됨. 문서만 실제 키에 맞게 갱신 필요 | 이번 Task 완료 보고에 기록, 필요 시 후속 문서 동기화 |
| Minor | 실 서버 대상 수동 검증 미실시 | 로컬 Temporal dev server 없이 정적/`TestWorkflowEnvironment` 검증만 수행 | T02 착수 전 로컬 dev server로 1회 수동 확인 권장 |

Critical/Important 등급 Gap 없음.

## 이번 변경과 무관한 기존 실패 (참고용)

`./gradlew.bat check` 실행 시 아래 7건이 실패하나 모두 `engine` 패키지 이전부터 존재하던 문제이며 이번 Task 범위 밖:

- `WorkerApplicationTests.contextLoads()` — 로컬 PostgreSQL(`localhost:15432`) 미기동으로 Flyway 마이그레이션 실패
- `AgentWorkerServiceTest` 4건, `PromptBuilderTest` 2건 — 기존 `java.util.UnknownFormatConversionException` (포맷 문자열 버그, 이번 Task와 무관)

## Decision

**Match Rate 99% (>= 90% 기준 충족)** — 반복 개선(iterate) 불필요. T01 완료로 판단하고 다음 Task(T02 Engine state)로 진행 가능.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-15 | Initial analysis — Match Rate 99% | Claude |
