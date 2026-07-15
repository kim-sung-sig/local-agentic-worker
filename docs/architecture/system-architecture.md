# Target System Architecture (Proposed)

이 문서는 현재 구현 현황이 아니라 승인 후 구축할 목표 아키텍처를 정의한다. 목표 시스템은 티켓을 정규화하고, AI 기반 개발 워크플로를 실행하며, 외부 개발 도구와 동기화한다.

시스템 다이어그램은 [Agent Worker System Diagram](../report/agent-worker-system-diagram.md)을 따른다.

## 1. Agent Worker Engine

Java와 Temporal로 구현하는 오케스트레이션 코어다. 실행 단계, 승인/반려, 재시도, 상태 이력만 책임진다. Git, 파일, 네트워크, 모델 호출은 직접 수행하지 않고 Temporal Activity 계약을 호출한다.

## 2. Agent Runtime and Adapters

프로젝트별 실행 노드가 worktree를 소유한다. Agent, QA, Source Control 구현체는 `WorkspaceRef`와 버전된 Activity DTO 계약을 통해 연결된다. 구현체는 Java, Python, TypeScript 등 어떤 언어로도 교체할 수 있다.

## 3. Ticket Sync

직접 등록과 GitHub Issues, Jira, Notion, Slack, Todo 등의 외부 입력을 정규화된 Ticket으로 변환한다. 구체적인 연동은 플러그인 구현체로 분리하며, Engine은 원본 시스템을 알지 못한다.

## Integration Rules

- Engine Workflow는 결정적 코드만 포함한다.
- CLI, API, Git, worktree, DB 조회 등 비결정적 I/O는 Activity에서 실행한다.
- Activity 입력과 출력은 버전된 JSON DTO이며, 대용량 산출물은 저장소 참조만 전달한다.
- 외부 변경 Activity는 Workflow ID와 단계/시도 횟수를 사용한 멱등 키를 가져야 한다.

## Implementation Status (T01–T08, docs/tasks/agent-worker-engine)

> [docs/tasks/agent-worker-engine/README.md](../tasks/agent-worker-engine/README.md)의 8개 Task가 전부 완료된 뒤 반영. 테스트 통과 확인 후에만 이 절을 갱신한다.

| Component | Status | 비고 |
|---|:---:|---|
| Temporal 배선(client/worker) | ✅ 구현됨 | `engine.infrastructure.temporal.TemporalConfiguration` |
| Engine 상태 모델(WorkflowRun/StageGate/AttemptRecord) + 영속성 | ✅ 구현됨 | `engine.domain.model.*`, `engine.infrastructure.datasource.*` |
| 버전 관리 Activity 계약(v1) | ✅ 구현됨 | `engine.application.contract.v1.*`, `engine.workflow.EngineActivities` |
| 6단계 Workflow + 게이트(승인/반려/재시도/취소) | ✅ 구현됨 | `engine.workflow.AgentWorkerWorkflow(Impl)` |
| Workspace 런타임(worktree 단일 소유권) | ✅ 구현됨 | `runtime.application.WorkspaceRuntime`, `runtime.infrastructure.git.GitWorktreeRuntime` |
| Implementation/QA 정책 기반 재시도 루프 | ✅ 구현됨 | `engine.application.service.AttemptPolicyResolver` |
| Draft PR·병합 게이트 | ✅ 구현됨 | `scm.application.SourceControlPlugin`, `scm.infrastructure.github.GitHubCliSourceControlPlugin` |
| Engine API(시작/조회/Attempt 이력/단계 결정) | ✅ 구현됨 | `engine.api.controller.WorkflowRunController` |
| Activity 구현체 연결 | ⚠️ 참조 구현만 | `engine.infrastructure.activity.EngineActivitiesImpl` — T02/T05/T07을 실제로 연결하지만, `assessTicket`/`planImplementation`/`implement`/`runQualityAssurance`는 결정론적 스텁이다. 실제 AI 기반 판단 로직은 별도 Agent Adapter 스펙에서 다룬다. |
| Ticket Sync | ❌ 미착수 | 외부 티켓 시스템(Jira/GitHub Issues 등) 연동은 이 8개 Task의 범위 밖 — 별도 스펙 필요 |

**검증 근거**: `./gradlew.bat test`(전체) 및 `./gradlew.bat check` 통과 — `engine`/`runtime`/`scm` 패키지 관련 실패 없음(기존 무관 실패 7건만 존재, `agent`/`issue`/`project` BC의 사전 결함). Testcontainers 기반 통합 테스트(`AgentWorkerEngineIntegrationTest`)는 로컬 Docker가 있는 환경에서 Intake→병합 완료 전 구간과 재사용된 WorkspaceRef, 실제 PostgreSQL에 저장된 Attempt를 검증한다.
