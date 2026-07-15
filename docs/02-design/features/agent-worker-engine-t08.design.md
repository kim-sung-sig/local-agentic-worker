# [Design] Agent Worker Engine — T08 Engine API, Observability, and Integration QA

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 조각들이 개별적으로 완성되어도 실제로 연결해 끝까지 동작시켜보지 않으면 통합 결함을 발견할 수 없다 |
| **SUCCESS** | API로 모든 Attempt의 산출물·QA 리포트 참조를 조회할 수 있고, 잘못된 단계 결정은 Workflow Run을 변경하지 않고 거부되며, 통합 테스트가 Intake부터 병합 완료까지 재사용된 WorkspaceRef 하나로 실행된다 |
| **SCOPE** | API 계층 + 참조용 Activity 구현체 + 통합 테스트 |

---

## 1. Overview

### 1.1 Design Goals

- API는 두 가지 서로 다른 소스에서 데이터를 읽는다: **실시간 상태**(현재 단계/상태)는 Temporal Workflow Query로, **Attempt 이력**은 T02 PostgreSQL 프로젝션으로 — 각자 가장 신뢰할 수 있는 소스를 사용한다.
- `EngineActivitiesImpl`은 "참조 구현"이다 — T02(영속성)/T05(워크스페이스)/T07(SCM)은 실제로 연결하고, AI/QA 판단이 필요한 4개 메서드(assessTicket/planImplementation/implement/runQualityAssurance)는 결정론적 스텁으로 대체해 통합 흐름 자체를 검증 가능하게 만든다.
- 통합 테스트는 T04~T07 각 Task의 테스트 인프라를 그대로 재사용한다: Temporal은 `TestWorkflowEnvironment`(T04), Workspace는 실제 임시 Git 저장소(T05), SCM은 테스트 대역(T07 패턴), 영속성만 Testcontainers 실제 PostgreSQL로 격상한다.

### 1.2 Architecture Decision — API가 읽는 두 소스

| 조회 대상 | 소스 | 이유 |
|---|---|---|
| 현재 단계/상태 | Temporal Workflow Query(`currentStage()`/`status()`, T04) | Workflow 실행 중에는 Temporal이 유일한 정답(source of truth) — DB 프로젝션은 아직 동기화되지 않음(T02/T04에서 명시적으로 이후 Task로 이월한 항목) |
| Attempt 이력 | T02 `AttemptRecordRepository` (PostgreSQL) | `EngineActivitiesImpl.recordAttemptHistory`가 이번 Task에서 실제로 이 Repository에 저장하므로 최초로 신뢰 가능한 데이터 소스가 됨 |

이 이원화는 임시방편이 아니라 스펙의 설계 그대로다: "UI와 Ticket Sync는 Workflow Run과 Attempt History를 조회해 각 루프의 산출물과 QA 보고서를 보여준다" — Workflow Run 자체(현재 단계)는 Temporal이, Attempt 이력은 DB가 책임진다.

---

## 2. API 명세

### 2.1 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/engine/workflow-runs` | Workflow Run 시작 |
| GET | `/api/engine/workflow-runs/{workflowRunId}` | 현재 단계/상태 조회 |
| GET | `/api/engine/workflow-runs/{workflowRunId}/attempts` | Attempt 이력 조회 |
| POST | `/api/engine/workflow-runs/{workflowRunId}/decisions` | 단계 결정(승인/반려/수정/재시도/취소) |

### 2.2 Request/Response

```java
public record StartWorkflowRequest(@NotBlank String ticketId, @NotBlank String rawSpecification) {}

public record StageDecisionRequest(
    @NotNull StageDecisionType decision,
    String reason,
    WorkflowStage targetStage
) {
    public enum StageDecisionType { APPROVE, REJECT, REQUEST_REVISION, RETRY, CANCEL }
}

public record WorkflowRunResponse(String workflowRunId, String currentStage, String status) {}

public record AttemptResponse(
    int attemptNumber, String implementationArtifactRef, String qaReportRef,
    Integer qaScore, String status, Instant createdAt, Instant finishedAt
) {
    public static AttemptResponse from(AttemptRecord record) { ... }
}
```

`WorkflowRunResponse`/`AttemptResponse` 어디에도 워크스페이스 파일시스템 경로나 `WorkspaceRef`를 포함하지 않는다 — API가 노출하는 것은 오직 단계·상태·산출물 참조 문자열(`ArtifactRef.value()` — 항상 `artifact://...` 형태의 불투명 식별자)뿐이다.

### 2.3 검증 및 에러 매핑

```java
private void validate(StageDecisionRequest request) {
    if (request.decision() == REJECT && request.targetStage() == null) {
        throw new BusinessException(ErrorCode.INVALID_STAGE_DECISION);
    }
    if (request.decision() == REQUEST_REVISION && (request.reason() == null || request.reason().isBlank())) {
        throw new BusinessException(ErrorCode.INVALID_STAGE_DECISION);
    }
}
```

검증은 Signal 전송 **이전**에 수행되므로, 검증 실패 시 Workflow Run은 어떤 영향도 받지 않는다(요청이 실패해도 Signal 자체가 만들어지지 않음).

신규 `ErrorCode`: `WORKFLOW_RUN_NOT_FOUND`(404 — Temporal `WorkflowNotFoundException`을 매핑), `INVALID_STAGE_DECISION`(400).

---

## 3. EngineActivitiesImpl (참조 구현)

```java
@Component
public class EngineActivitiesImpl implements EngineActivities {

    private final WorkspaceRuntime workspaceRuntime;
    private final SourceControlPlugin sourceControlPlugin;
    private final WorkflowRunRepository workflowRunRepository;

    // assessTicket / planImplementation / implement / runQualityAssurance / sendNotification:
    //   결정론적 참조 스텁 — 항상 성공하는 최소 응답을 반환한다(실제 AI/QA 판단은 범위 밖).

    // prepareWorkspace → workspaceRuntime.acquire(workflowRunId, "feature/"+workflowRunId, "main")
    // recordAttemptHistory → WorkflowRun을 로드(없으면 생성) 후 AttemptRecord를 append하고 저장
    // manageSourceControl → action에 따라 sourceControlPlugin.createDraftPullRequest / mergePullRequest 호출
}
```

**단순화 사항(명시적으로 문서화)**: base 브랜치를 `"main"`으로 고정한다 — 이는 T05/T07이 "하드코딩 금지"라고 요구한 **플러그인 자체**의 규칙이 아니라, 이번 참조 구현이 아직 연결되지 않은 Ticket/Project 설정(실제 base 브랜치 출처)을 대신하는 임시값이다. `GitWorktreeRuntime`/`GitHubCliSourceControlPlugin`은 여전히 호출자가 넘긴 값만 사용하도록 구현되어 있어(T05/T07에서 검증됨), 실제 Agent Adapter가 이 참조 구현을 교체할 때 실제 base 브랜치 값을 넘기기만 하면 된다.

`ArtifactRef`/`QaResult`의 값은 항상 `"artifact://{workflowRunId}/..."` 형태의 합성 식별자이며 실제 파일시스템 경로를 담지 않는다 — API 응답으로 노출돼도 안전하다.

---

## 4. Test Plan (TDD)

### 4.1 WorkflowRunControllerTest (`@WebMvcTest`)

| # | Test | Expected |
|---|------|----------|
| 1 | 정상 시작 요청 | 202 Accepted, `workflowRunId` 반환 |
| 2 | 시작 요청 검증 실패(빈 ticketId) | 400 |
| 3 | 정상 조회 | 200, 현재 단계/상태 반환 |
| 4 | 존재하지 않는 Workflow Run 조회 | 404 |
| 5 | Attempt 이력 조회 | 200, `AttemptResponse` 목록 |
| 6 | REJECT without targetStage | 400, Signal 미전송(mock 검증) |
| 7 | REQUEST_REVISION without reason | 400, Signal 미전송 |
| 8 | 정상 APPROVE 결정 | 202, `approve()` Signal 호출 검증 |

### 4.2 AgentWorkerEngineIntegrationTest

Docker 미가용 시 `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable())`로 스킵.

| # | Test | Expected |
|---|------|----------|
| 1 | Intake→Planning→Workspace→Implementation→QA→Review/Merge 전 구간을 승인과 함께 실행 | 최종 상태 COMPLETED |
| 2 | 전 구간에서 `prepareWorkspace`(→`GitWorktreeRuntime.acquire`)가 정확히 1회만 실행 | 재사용된 WorkspaceRef 하나 확인 |
| 3 | `AttemptRecordRepository`(Testcontainers PostgreSQL)에서 Attempt 1건 조회 | 산출물/QA 참조/점수/상태/시각 모두 채워짐 |

---

## 5. Implementation Guide

### 5.1 File Structure

```
src/main/java/com/example/worker/engine/
├── api/
│   ├── controller/WorkflowRunController.java
│   ├── request/StartWorkflowRequest.java
│   ├── request/StageDecisionRequest.java
│   ├── response/WorkflowRunResponse.java
│   └── response/AttemptResponse.java
└── infrastructure/activity/
    └── EngineActivitiesImpl.java

src/test/java/com/example/worker/engine/
├── api/controller/WorkflowRunControllerTest.java
└── integration/AgentWorkerEngineIntegrationTest.java
```

### 5.2 Implementation Order (TDD)

1. [ ] `common.exception.ErrorCode`에 `WORKFLOW_RUN_NOT_FOUND`/`INVALID_STAGE_DECISION` 추가
2. [ ] API request/response record 정의
3. [ ] `WorkflowRunControllerTest` 작성 — §4.1의 8개 시나리오 먼저 (Red)
4. [ ] `WorkflowRunController` 구현 (Green)
5. [ ] `EngineActivitiesImpl` 구현, `TemporalConfiguration`에 등록
6. [ ] `build.gradle`에 Testcontainers 테스트 의존성 추가
7. [ ] `AgentWorkerEngineIntegrationTest` 작성 및 실행(Docker 가용 시)
8. [ ] `docs/architecture/system-architecture.md` 갱신(테스트 통과 후)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
