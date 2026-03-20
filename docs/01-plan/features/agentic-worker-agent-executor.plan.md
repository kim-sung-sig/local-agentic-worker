# [Plan] Agentic Worker — Agent Executor (Phase 2)

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agentic-worker-agent-executor |
| 작성일 | 2026-03-20 |
| 상태 | Plan |
| 의존 | agentic-worker (Phase 1 완료) |
| 기술 스택 | Java 21 + Spring Boot 3.5.12 + ProcessBuilder + Claude CLI + GitHub CLI |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | Kafka로 이슈 이벤트를 수신했지만 실제 브랜치 생성·코드 작성·PR 제출까지 이어지는 실행 파이프라인이 없어 에이전트 자동화가 미완성 |
| **Solution** | `GitBranchService` → `ClaudeAgentExecutor` → `PullRequestService` 3단계 파이프라인 + `AgentWorkerService` 오케스트레이터 구현 |
| **Function UX Effect** | 이슈 생성만으로 자동 브랜치 생성 → Claude AI 코드 작성 → Draft PR 생성까지 완전 자동화; 이슈 상태도 실시간 반영 |
| **Core Value** | 서버 비용 Zero, Claude Pro 구독만으로 로컬 AI 에이전트 파이프라인 완성 |

---

## 1. Problem Statement

### 1-1. 현재 상태 (Phase 1 완료 이후)

```
[IssueCreatedEvent] → Kafka Consumer 수신 → 로그 출력 → ❌ 중단 (TODO)
```

`IssueCreatedEventConsumer`에 `// TODO: agentWorkerService.handle(event)` 만 존재.
실제 에이전트 실행 파이프라인이 없어 이슈 생성 후 아무 일도 일어나지 않는다.

### 1-2. 목표 상태 (Phase 2 완료 이후)

```
[IssueCreatedEvent] → Kafka Consumer
  → AgentWorkerService.handle(event)
      → Issue 상태: OPEN → IN_PROGRESS
      → GitBranchService.createBranch(localPath, baseBranch, branchName)
      → ClaudeAgentExecutor.execute(workDir, prompt)
      → PullRequestService.createDraftPr(localPath, branchName, title, body)
      → Issue 상태: IN_PROGRESS → IN_REVIEW
      ↓ (실패 시)
      → Issue 상태: IN_PROGRESS → FAILED
```

---

## 2. Goals / Non-Goals

### Goals (이번 단계 범위)

- [x] `AgentJob` 도메인 엔티티 (agent BC) — 실행 이력 저장
- [x] `GitBranchService` — `ProcessBuilder`로 git 명령 실행 (checkout baseBranch → pull → checkout -b branchName)
- [x] `ClaudeAgentExecutor` — `ProcessBuilder`로 `claude` CLI 실행 (--print --dangerously-skip-permissions)
- [x] `PullRequestService` — `gh pr create --draft` 실행
- [x] `AgentWorkerService` — 3단계 파이프라인 오케스트레이션
- [x] Issue 상태 자동 업데이트 (Spring Modulith 이벤트 경유)
- [x] DB migration V2 — `agent_job` 테이블 추가
- [x] `IssueCreatedEventConsumer` TODO 제거 및 실제 호출 연결

### Non-Goals (이번 단계 제외)

- GitHub API 직접 호출 (gh CLI 사용)
- 멀티 에이전트 병렬 실행
- DLT(Dead Letter Topic) 재시도 전략
- 인증 / 권한
- 실패 후 자동 재시도

---

## 3. Domain Model 확장

### 3-1. AgentJob (신규 — agent BC)

```
AgentJob
  - id: AgentJobId (UUID)
  - issueId: UUID
  - projectId: UUID
  - branchName: String
  - status: AgentJobStatus
  - startedAt: Instant
  - finishedAt: Instant (nullable)
  - errorMessage: String (nullable)
```

### 3-2. AgentJobStatus

```
PENDING → RUNNING → SUCCEEDED
                  → FAILED
```

### 3-3. Issue 상태 전이 연동

| 단계 | Issue Status |
|------|-------------|
| AgentWorkerService 시작 | IN_PROGRESS |
| PullRequestService 성공 | IN_REVIEW |
| 어느 단계든 예외 발생 | FAILED |

---

## 4. 이벤트 설계

### Phase 2 이벤트 흐름

```
agent BC → issue BC (단방향 이벤트)
```

| 이벤트 | 발행 | 수신 |
|--------|------|------|
| `IssueStatusChangedEvent(issueId, status)` | `agent/event/` | `issue/application/` |

Spring Modulith `@ApplicationModuleListener`로 수신 — Kafka 외부화 불필요 (내부 이벤트).

---

## 5. Service 설계

### 5-1. GitBranchService

```java
// agent/application/service/GitBranchService.java
public String createBranch(String localPath, String baseBranch, String branchName)
// git checkout {baseBranch} && git pull origin {baseBranch} && git checkout -b {branchName}
// throws AgentExecutionException on non-zero exit
```

### 5-2. ClaudeAgentExecutor

```java
// agent/application/service/ClaudeAgentExecutor.java
public String execute(String workDir, String prompt)
// claude --print --dangerously-skip-permissions -p "{prompt}"
// 타임아웃: 10분 (configurable)
// stdout 전체 캡처 후 반환
```

### 5-3. PullRequestService

```java
// agent/application/service/PullRequestService.java
public String createDraftPr(String localPath, String branchName, String title, String body)
// git push origin {branchName}
// gh pr create --draft --title "{title}" --body "{body}" --base {baseBranch}
// PR URL 반환
```

### 5-4. AgentWorkerService (오케스트레이터)

```java
// agent/application/service/AgentWorkerService.java
public void handle(IssueCreatedEvent event)
// 1. AgentJob 생성 (PENDING)
// 2. publishEvent(IssueStatusChangedEvent(issueId, IN_PROGRESS))
// 3. job.start()  → status = RUNNING
// 4. branchName = "feat/issue-{number}-{slug}"
// 5. gitBranchService.createBranch(...)
// 6. prompt = PromptBuilder.build(event)
// 7. claudeAgentExecutor.execute(workDir, prompt)
// 8. pullRequestService.createDraftPr(...)
// 9. job.complete(prUrl)
// 10. publishEvent(IssueStatusChangedEvent(issueId, IN_REVIEW))
// catch Exception → job.fail(e.getMessage()) → publishEvent(FAILED)
```

---

## 6. Prompt 설계

```
당신은 시니어 개발자입니다.

## 이슈
- 번호: #{issueNumber}
- 제목: {title}
- 우선순위: {priority}
- 설명:
{description}

## 작업
1. 현재 코드베이스를 분석하세요.
2. 이슈를 해결하는 최소한의 코드 변경을 구현하세요.
3. 기존 컨벤션을 따르세요.
4. 변경 완료 후 `git add -A && git commit -m "feat: #{issueNumber} {title}"` 을 실행하세요.
```

---

## 7. DB Migration

### V2__add_agent_job.sql

```sql
CREATE TABLE agent_job (
    id            UUID PRIMARY KEY,
    issue_id      UUID NOT NULL,
    project_id    UUID NOT NULL,
    branch_name   VARCHAR(200) NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    started_at    TIMESTAMP NOT NULL,
    finished_at   TIMESTAMP,
    error_message TEXT,
    pr_url        VARCHAR(500)
);
```

---

## 8. 클래스 구조 (agent BC 확장)

```
agent/
├── domain/
│   └── model/
│       ├── AgentJob.java           (엔티티)
│       ├── AgentJobId.java         (VO)
│       └── AgentJobStatus.java     (Enum)
├── event/
│   └── model/
│       └── IssueStatusChangedEvent.java  (내부 이벤트)
├── application/
│   ├── port/
│   │   └── AgentJobRepository.java
│   └── service/
│       ├── AgentWorkerService.java
│       ├── GitBranchService.java
│       ├── ClaudeAgentExecutor.java
│       └── PullRequestService.java
└── infrastructure/
    ├── datasource/
    │   ├── AgentJobJpaEntity.java
    │   ├── AgentJobJpaRepository.java
    │   └── AgentJobRepositoryAdapter.java
    └── kafka/
        └── IssueCreatedEventConsumer.java  (TODO 제거 → handle() 호출)

issue/ (수정)
└── application/
    └── listener/
        └── IssueStatusChangedEventListener.java  (@ApplicationModuleListener)
```

---

## 9. 위험 요소 및 대응

| 위험 | 대응 |
|------|------|
| Claude CLI 미설치 | `AgentExecutionException` + FAILED 상태 기록 |
| gh CLI 미설치 / 미인증 | 동일 예외 처리 |
| claude 실행 타임아웃 | `ProcessBuilder`에 10분 타임아웃 설정 |
| git dirty working tree | 브랜치 생성 전 `git status` 확인, 충돌 시 FAILED |
| Spring Modulith 이벤트 순환 | agent BC → issue BC 단방향만 허용 |

---

## 10. 구현 순서

1. V2 DB migration 추가
2. `AgentJob` 도메인 모델 + Value Objects
3. `AgentJobRepository` port + JPA adapter
4. `IssueStatusChangedEvent` 내부 이벤트
5. `IssueStatusChangedEventListener` (issue BC)
6. `GitBranchService`
7. `ClaudeAgentExecutor`
8. `PullRequestService`
9. `AgentWorkerService` 오케스트레이터
10. `IssueCreatedEventConsumer` TODO 연결

---

## 11. Open Questions

- Claude CLI 경로가 PATH에 없을 경우 대응? → 설정에서 `claude.cli.path` 프로퍼티로 주입
- GitHub 이외 Git 호스팅(GitLab, Gitea)? → 이번 단계는 `gh` CLI (GitHub 한정)
- PR body에 Claude 출력 전체를 넣을 것인가? → 요약만 포함, 전체는 truncate
