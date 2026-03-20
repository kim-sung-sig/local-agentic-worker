# [Design] Agentic Worker — Agent Executor (Phase 2)

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agentic-worker-agent-executor |
| 작성일 | 2026-03-20 |
| 상태 | Design |
| 의존 | agentic-worker Phase 1 (project BC, issue BC, Kafka consumer skeleton) |

---

## 1. 아키텍처 개요

```
issue BC                    agent BC                     외부 프로세스
─────────                   ────────                     ────────────
IssueCreatedEvent
  @Externalized             IssueCreatedEventConsumer
  → Kafka topic    ─────►    .consume(event)
                              │
                              ▼
                           AgentWorkerService
                              │
                    ┌─────────┼──────────────┐
                    ▼         ▼              ▼
                 GitBranch  ClaudeAgent  PullRequest
                 Service    Executor     Service
                    │         │              │
                    ▼         ▼              ▼
                  git CLI  claude CLI    git + gh CLI
                  (Process) (Process)    (Process)
                              │
                    ┌─────────┘
                    ▼
           IssueStatusChangedEvent  ─────►  IssueStatusChangedEventListener
           (Spring Modulith internal)         (issue BC) → IssueCommandService
```

---

## 2. 클래스 다이어그램

### 2-1. agent BC — domain

```
AgentJobId(record)
  + value: UUID
  + newId(): AgentJobId
  + of(UUID): AgentJobId

AgentJobStatus(enum)
  PENDING, RUNNING, SUCCEEDED, FAILED

AgentJob
  - id: AgentJobId
  - issueId: UUID
  - projectId: UUID
  - branchName: String
  - status: AgentJobStatus
  - startedAt: Instant
  - finishedAt: Instant?
  - errorMessage: String?
  - prUrl: String?
  + create(issueId, projectId, branchName): AgentJob  [factory]
  + start(): void           [PENDING → RUNNING]
  + complete(prUrl): void   [RUNNING → SUCCEEDED]
  + fail(errorMsg): void    [RUNNING → FAILED]
```

### 2-2. agent BC — application

```
AgentJobRepository (port)
  + save(AgentJob): AgentJob
  + findById(AgentJobId): Optional<AgentJob>
  + findByIssueId(UUID): List<AgentJob>

AgentWorkerService
  - agentJobRepository: AgentJobRepository
  - gitBranchService: GitBranchService
  - claudeAgentExecutor: ClaudeAgentExecutor
  - pullRequestService: PullRequestService
  - eventPublisher: ApplicationEventPublisher
  + handle(IssueCreatedEvent): void

GitBranchService
  + createBranch(localPath: String, baseBranch: String, branchName: String): void

ClaudeAgentExecutor
  - claudeCliPath: String          [from @Value("${agent.claude.cli-path:claude}")]
  - timeoutMinutes: int            [from @Value("${agent.claude.timeout-minutes:10}")]
  + execute(workDir: String, prompt: String): String

PullRequestService
  + push(localPath: String, branchName: String): void
  + createDraftPr(localPath: String, baseBranch: String, branchName: String, title: String, body: String): String
```

### 2-3. 이벤트

```
IssueStatusChangedEvent (record)
  + issueId: UUID
  + newStatus: String    // "IN_PROGRESS" | "IN_REVIEW" | "FAILED"
  + occurredAt: Instant

IssueStatusChangedEventListener (issue BC)
  @ApplicationModuleListener
  + on(IssueStatusChangedEvent): void
    → issueCommandService.updateStatus(issueId, newStatus)
```

---

## 3. 시퀀스 다이어그램

### 3-1. 정상 흐름

```
Kafka           Consumer           AgentWorker        Git/Claude/GH
──────          ────────           ───────────        ─────────────
IssueCreated
  ──────────►  consume(event)
                  │
                  ▼
              handle(event)
                  │─── AgentJob.create()  ─────────────►  DB(save)
                  │─── publishEvent(IN_PROGRESS)
                  │
                  │─── branchName = "feat/issue-{n}-{slug}"
                  │─── gitBranchService.createBranch()  ──►  git checkout/pull/checkout -b
                  │
                  │─── prompt = PromptBuilder.build(event)
                  │─── claudeExecutor.execute()  ──────────►  claude --print ...
                  │                                              (10분 타임아웃)
                  │─── pullRequestService.push()  ─────────►  git push origin
                  │─── pullRequestService.create()  ───────►  gh pr create --draft
                  │
                  │─── job.complete(prUrl)
                  └─── publishEvent(IN_REVIEW)
```

### 3-2. 실패 흐름

```
AgentWorker        issue BC
───────────        ────────
handle(event)
  try { ... }
  catch (Exception e)
      job.fail(e.getMessage())
      publishEvent(IssueStatusChangedEvent(issueId, "FAILED"))
                        │
                        ▼
              IssueStatusChangedEventListener
                        │
                        ▼
              issueCommandService.updateStatus(issueId, FAILED)
```

---

## 4. DB Migration

### V2__add_agent_job.sql

```sql
CREATE TABLE agent_job (
    id            UUID PRIMARY KEY,
    issue_id      UUID NOT NULL,
    project_id    UUID NOT NULL,
    branch_name   VARCHAR(200) NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    started_at    TIMESTAMP    NOT NULL,
    finished_at   TIMESTAMP,
    error_message TEXT,
    pr_url        VARCHAR(500),
    CONSTRAINT fk_agent_job_issue FOREIGN KEY (issue_id) REFERENCES issue(id)
);

CREATE INDEX idx_agent_job_issue_id ON agent_job(issue_id);
```

---

## 5. application.yml 추가 설정

```yaml
agent:
  claude:
    cli-path: claude          # PATH에 없으면 절대경로 지정
    timeout-minutes: 10
```

---

## 6. PromptBuilder 설계

```java
// agent/application/service/PromptBuilder.java (static utility)
public static String build(IssueCreatedEvent event) {
    return """
        당신은 시니어 개발자입니다.

        ## 이슈
        - 번호: #%d
        - 제목: %s
        - 우선순위: %s
        - 설명:
        %s

        ## 작업 지시
        1. 현재 코드베이스를 분석하세요.
        2. 이슈를 해결하는 최소한의 코드 변경을 구현하세요.
        3. 기존 컨벤션과 아키텍처를 따르세요.
        4. 모든 변경 완료 후 반드시 다음 명령을 실행하세요:
           git add -A && git commit -m "feat: #%d %s"
        """.formatted(
            event.issueNumber(), event.title(), event.priority(),
            event.description() == null ? "(설명 없음)" : event.description(),
            event.issueNumber(), event.title()
        );
}
```

---

## 7. 예외 처리

```java
// agent/application/exception/AgentExecutionException.java
public class AgentExecutionException extends RuntimeException {
    public AgentExecutionException(String message) { super(message); }
    public AgentExecutionException(String message, Throwable cause) { super(message, cause); }
}
```

`GitBranchService`, `ClaudeAgentExecutor`, `PullRequestService` 모두 실패 시 `AgentExecutionException` throw.
`AgentWorkerService`가 catch → `job.fail()` + 이벤트 발행.

---

## 8. 구현 체크리스트

### infrastructure
- [ ] `src/main/resources/db/migration/V2__add_agent_job.sql`
- [ ] `src/main/resources/application.yml` — `agent.claude.*` 설정 추가

### agent BC — domain
- [ ] `agent/domain/model/AgentJobId.java`
- [ ] `agent/domain/model/AgentJobStatus.java`
- [ ] `agent/domain/model/AgentJob.java`

### agent BC — event
- [ ] `agent/event/model/IssueStatusChangedEvent.java`

### agent BC — application
- [ ] `agent/application/port/AgentJobRepository.java`
- [ ] `agent/application/exception/AgentExecutionException.java`
- [ ] `agent/application/service/PromptBuilder.java`
- [ ] `agent/application/service/GitBranchService.java`
- [ ] `agent/application/service/ClaudeAgentExecutor.java`
- [ ] `agent/application/service/PullRequestService.java`
- [ ] `agent/application/service/AgentWorkerService.java`

### agent BC — infrastructure
- [ ] `agent/infrastructure/datasource/AgentJobJpaEntity.java`
- [ ] `agent/infrastructure/datasource/AgentJobJpaRepository.java`
- [ ] `agent/infrastructure/datasource/AgentJobRepositoryAdapter.java`
- [ ] `agent/infrastructure/kafka/IssueCreatedEventConsumer.java` (TODO 제거)

### issue BC — 수정
- [ ] `issue/application/listener/IssueStatusChangedEventListener.java`
