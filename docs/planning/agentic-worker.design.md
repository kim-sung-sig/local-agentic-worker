# [Design] Agentic Worker — 상세 설계 문서

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agentic-worker |
| 작성일 | 2026-03-20 |
| 상태 | Design |
| 참조 Plan | [agentic-worker.plan.md](agentic-worker.plan.md) |

---

## 1. 아키텍처 개요

### 1-1. 계층 구조

```
┌─────────────────────────────────────────────────────────────┐
│  [Browser]  Vue 3 (CDN) + Axios                             │
│             src/main/resources/static/                      │
└────────────────────────┬────────────────────────────────────┘
                         │ REST (JSON)
┌────────────────────────▼────────────────────────────────────┐
│  Spring Boot 3.5.12  (port 18081)                           │
│  ├── project BC   (api/ → application/ → domain/)           │
│  ├── issue   BC   (api/ → application/ → domain/)           │
│  └── agent   BC   (kafka consumer → application/)           │
│                                                             │
│  Spring Modulith Events ──► Kafka (issue-created topic)     │
└─────────────────────────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│  PostgreSQL (localhost:5432 / agentic_worker DB)            │
│  Kafka      (localhost:29092)                               │
└─────────────────────────────────────────────────────────────┘
```

### 1-2. UI 기술 결정: Thymeleaf 제거 → Vue 3 CDN

| 항목 | 결정 |
|------|------|
| 렌더링 방식 | SPA (Spring Boot 정적 파일 서빙) |
| Vue 버전 | Vue 3 (CDN — `unpkg.com/vue@3`) |
| HTTP 클라이언트 | Axios (CDN) |
| 라우팅 | Vue Router 4 (CDN) |
| 빌드 도구 | **없음** — 순수 CDN, 별도 npm/vite 불필요 |
| 서빙 경로 | `src/main/resources/static/` |

> **이유**: 빌드 파이프라인 없이 Vue 반응성을 그대로 활용. Spring Boot가 정적 파일을 `/`로 서빙하며, REST API는 `/api/**`로 분리.

---

## 2. Spring Modulith 모듈 구조

```
com.example.worker
├── project/                  ← Project Bounded Context
├── issue/                    ← Issue Bounded Context
└── agent/                    ← Agent Bounded Context
```

### 모듈 간 의존 규칙

```
project ← issue (issue가 projectId 참조)
issue   → agent (IssueCreatedEvent via Spring Modulith)
agent   ← (독립, 외부 이벤트만 수신)
```

- `project` BC와 `agent` BC는 직접 의존 없음.
- 모듈 간 통신: Spring Modulith Application Event → Kafka externalization.

---

## 3. 도메인 모델

### 3-1. Project BC

```java
// domain/model/Project.java
public class Project {
    private ProjectId id;           // UUID VO
    private String name;            // 프로젝트 이름
    private LocalPath localPath;    // Git 레포 절대경로 VO (유효성 검증 포함)
    private BranchName baseBranch;  // 기준 브랜치 VO
    private LocalDateTime createdAt;

    // 생성 팩토리
    public static Project create(String name, String localPath, String baseBranch) { ... }
}

// domain/model/ProjectId.java  (Value Object)
public record ProjectId(UUID value) { ... }

// domain/model/LocalPath.java  (Value Object)
public record LocalPath(String value) {
    public LocalPath {
        if (!Files.isDirectory(Path.of(value)))
            throw new IllegalArgumentException("유효하지 않은 경로: " + value);
    }
}

// domain/model/BranchName.java  (Value Object)
public record BranchName(String value) {
    public BranchName { Objects.requireNonNull(value); }
}
```

### 3-2. Issue BC

```java
// domain/model/Issue.java
public class Issue {
    private IssueId id;                // UUID VO
    private ProjectId projectId;       // 외래 참조 (VO)
    private IssueNumber issueNumber;   // 프로젝트 내 순번 VO
    private String title;
    private String description;
    private Priority priority;         // Enum: LOW / MEDIUM / HIGH / CRITICAL
    private IssueStatus status;        // Enum: OPEN / IN_PROGRESS / IN_REVIEW / FAILED / CLOSED
    private LocalDateTime createdAt;

    // 상태 전이
    public void startProgress() { this.status = IssueStatus.IN_PROGRESS; }
    public void markInReview()  { this.status = IssueStatus.IN_REVIEW; }
    public void markFailed()    { this.status = IssueStatus.FAILED; }
    public void close()         { this.status = IssueStatus.CLOSED; }
}

// domain/model/IssueNumber.java  (Value Object)
public record IssueNumber(int value) {
    public IssueNumber { if (value < 1) throw new IllegalArgumentException(); }
}

// Enum: Priority
public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

// Enum: IssueStatus
public enum IssueStatus { OPEN, IN_PROGRESS, IN_REVIEW, FAILED, CLOSED }
```

### 3-3. Agent BC

```java
// domain/model/AgentJob.java  (다음 단계 준비용 — 현재는 skeleton)
public class AgentJob {
    private AgentJobId id;
    private IssueId issueId;
    private AgentJobStatus status;     // QUEUED / RUNNING / SUCCEEDED / FAILED
    private String branchName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
}
```

---

## 4. JPA 엔티티 설계

### 4-1. ProjectJpaEntity

```java
// project/infrastructure/datasource/ProjectJpaEntity.java
@Entity @Table(name = "project")
public class ProjectJpaEntity {
    @Id UUID id;
    @Column(nullable = false) String name;
    @Column(nullable = false, unique = true) String localPath;
    @Column(nullable = false) String baseBranch;
    @Column(nullable = false) LocalDateTime createdAt;

    // domain ↔ entity 변환
    public static ProjectJpaEntity from(Project domain) { ... }
    public Project toDomain() { ... }
}
```

### 4-2. IssueJpaEntity

```java
// issue/infrastructure/datasource/IssueJpaEntity.java
@Entity @Table(name = "issue",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "issue_number"}))
public class IssueJpaEntity {
    @Id UUID id;
    @Column(name = "project_id", nullable = false) UUID projectId;
    @Column(nullable = false) int issueNumber;
    @Column(nullable = false) String title;
    @Column(columnDefinition = "TEXT") String description;
    @Enumerated(EnumType.STRING) Priority priority;
    @Enumerated(EnumType.STRING) IssueStatus status;
    @Column(nullable = false) LocalDateTime createdAt;
}
```

### 4-3. DDL (Flyway V1)

```sql
-- V1__init_schema.sql
CREATE TABLE project (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    local_path  VARCHAR(500) NOT NULL UNIQUE,
    base_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    created_at  TIMESTAMP    NOT NULL
);

CREATE TABLE issue (
    id           UUID         PRIMARY KEY,
    project_id   UUID         NOT NULL REFERENCES project(id),
    issue_number INTEGER      NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    priority     VARCHAR(20)  NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uq_issue_per_project UNIQUE (project_id, issue_number)
);

CREATE INDEX idx_issue_project_id ON issue (project_id);
```

---

## 5. Application Service 설계

### 5-1. ProjectCommandService

```java
public class ProjectCommandService {
    // Use Case: 프로젝트 등록
    public ProjectId registerProject(String name, String localPath, String baseBranch) {
        // 1. localPath 중복 검사
        // 2. Project.create() — 도메인 검증 포함
        // 3. ProjectRepository.save()
        // return ProjectId
    }
}

public class ProjectQueryService {
    public List<ProjectSummary> listProjects() { ... }
    public ProjectDetail getProject(UUID id) { ... }
}
```

### 5-2. IssueCommandService

```java
public class IssueCommandService {
    // Use Case: 이슈 생성
    @Transactional
    public IssueId createIssue(UUID projectId, String title, String description, Priority priority) {
        // 1. Project 존재 확인
        // 2. 다음 issueNumber 채번 (SELECT MAX + 1, 트랜잭션 내)
        // 3. Issue.create()
        // 4. IssueRepository.save()
        // 5. ApplicationEventPublisher.publishEvent(IssueCreatedEvent) ← Spring Modulith
    }

    // Use Case: 상태 변경
    @Transactional
    public void updateStatus(UUID issueId, IssueStatus newStatus) { ... }
}
```

### 5-3. IssueNumber 채번 전략

```java
// issue/application/service/IssueNumberSequenceService.java
@Service
public class IssueNumberSequenceService {
    // SELECT COALESCE(MAX(issue_number), 0) + 1 FROM issue WHERE project_id = ?
    // 트랜잭션 내 실행 (IssueCommandService @Transactional 전파)
    public int nextNumber(UUID projectId) { ... }
}
```

---

## 6. 이벤트 설계 (Spring Modulith + Kafka)

### 6-1. 이벤트 페이로드

```java
// issue/event/model/IssueCreatedEvent.java
@Externalized("issue-created")   // Spring Modulith Kafka externalization
public record IssueCreatedEvent(
    UUID issueId,
    int issueNumber,
    String title,
    String description,
    String priority,
    UUID projectId,
    String projectLocalPath,
    String baseBranch,
    Instant occurredAt
) {}
```

### 6-2. 이벤트 발행 흐름

```
IssueCommandService
  └─ applicationEventPublisher.publishEvent(IssueCreatedEvent)
       │
       └─ Spring Modulith Transactional Outbox (spring-modulith-starter-jpa)
            │  (트랜잭션 커밋 후 발행 보장)
            └─ Kafka Producer → topic: "issue-created"
```

### 6-3. Kafka Consumer (agent BC)

```java
// agent/infrastructure/kafka/IssueCreatedEventConsumer.java
@Component
public class IssueCreatedEventConsumer {

    @KafkaListener(topics = "issue-created", groupId = "agent-worker")
    public void consume(IssueCreatedEvent event) {
        // Phase 1 (현재): 수신 로깅만
        log.info("[AgentWorker] Issue 수신: #{} - {}", event.issueNumber(), event.title());

        // Phase 2 (다음 단계):
        // agentWorkerService.handle(event);
        // → GitBranchService.createBranch()
        // → ClaudeAgentExecutor.execute()
        // → PullRequestService.createDraftPr()
    }
}
```

---

## 7. REST API 설계

### 7-1. ProjectController

```
POST   /api/projects              프로젝트 등록
GET    /api/projects              프로젝트 목록
GET    /api/projects/{id}         프로젝트 상세
```

```java
// request
public record CreateProjectRequest(
    @NotBlank String name,
    @NotBlank String localPath,
    @NotBlank String baseBranch
) {}

// response
public record ProjectResponse(
    UUID id, String name, String localPath,
    String baseBranch, LocalDateTime createdAt
) {
    public static ProjectResponse from(Project p) { ... }
}
```

### 7-2. IssueController

```
POST   /api/projects/{projectId}/issues      이슈 생성
GET    /api/projects/{projectId}/issues      이슈 목록
GET    /api/issues/{id}                      이슈 상세
PATCH  /api/issues/{id}/status               상태 변경
```

```java
// request
public record CreateIssueRequest(
    @NotBlank String title,
    String description,
    @NotNull Priority priority
) {}

public record UpdateIssueStatusRequest(
    @NotNull IssueStatus status
) {}

// response
public record IssueResponse(
    UUID id, UUID projectId, int issueNumber,
    String title, String description,
    Priority priority, IssueStatus status,
    LocalDateTime createdAt
) {
    public static IssueResponse from(Issue i) { ... }
}
```

### 7-3. 에러 응답 형식

```json
{
  "code": "PROJECT_NOT_FOUND",
  "message": "프로젝트를 찾을 수 없습니다: {id}",
  "timestamp": "2026-03-20T10:00:00Z"
}
```

---

## 8. UI 설계 (Vue 3 CDN)

### 8-1. 파일 구조

```
src/main/resources/static/
├── index.html              ← SPA 진입점, Vue 3 + Vue Router CDN 로드
├── css/
│   └── app.css             ← 기본 스타일 (Linear 스타일 단순화)
└── js/
    ├── app.js              ← Vue 앱 초기화 + 라우터 등록
    ├── router.js           ← Vue Router 4 라우트 정의
    ├── api.js              ← Axios 인스턴스 + API 함수
    └── components/
        ├── ProjectList.js   ← 프로젝트 목록 컴포넌트
        ├── ProjectForm.js   ← 프로젝트 등록 폼
        ├── IssueList.js     ← 이슈 목록 (프로젝트별)
        ├── IssueForm.js     ← 이슈 생성 폼
        └── IssueDetail.js   ← 이슈 상세 + 상태 변경
```

### 8-2. index.html 구조

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>Agentic Worker</title>
  <link rel="stylesheet" href="/css/app.css">
</head>
<body>
  <div id="app"></div>

  <!-- Vue 3 CDN -->
  <script src="https://unpkg.com/vue@3.4.0/dist/vue.global.prod.js"></script>
  <!-- Vue Router 4 CDN -->
  <script src="https://unpkg.com/vue-router@4.3.0/dist/vue-router.global.prod.js"></script>
  <!-- Axios CDN -->
  <script src="https://unpkg.com/axios@1.6.0/dist/axios.min.js"></script>

  <script type="module" src="/js/api.js"></script>
  <script type="module" src="/js/components/ProjectList.js"></script>
  <script type="module" src="/js/components/IssueList.js"></script>
  <!-- ... 기타 컴포넌트 -->
  <script type="module" src="/js/router.js"></script>
  <script type="module" src="/js/app.js"></script>
</body>
</html>
```

> **주의**: CDN URL은 버전 고정. `vue.global.prod.js` 사용 (ESM 아님 — module import 없이 전역 `Vue` 객체).

### 8-3. 라우트 구조

| Path | 컴포넌트 | 설명 |
|------|----------|------|
| `/` | ProjectList | 프로젝트 목록 |
| `/projects/new` | ProjectForm | 프로젝트 등록 |
| `/projects/:id` | IssueList | 프로젝트 상세 + 이슈 목록 |
| `/projects/:id/issues/new` | IssueForm | 이슈 생성 |
| `/issues/:id` | IssueDetail | 이슈 상세 |

### 8-4. API 클라이언트 (api.js)

```javascript
// js/api.js
const http = axios.create({ baseURL: '/api' });

const ProjectApi = {
  list:   ()        => http.get('/projects'),
  get:    (id)      => http.get(`/projects/${id}`),
  create: (payload) => http.post('/projects', payload),
};

const IssueApi = {
  list:         (projectId)         => http.get(`/projects/${projectId}/issues`),
  get:          (id)                => http.get(`/issues/${id}`),
  create:       (projectId, payload)=> http.post(`/projects/${projectId}/issues`, payload),
  updateStatus: (id, status)        => http.patch(`/issues/${id}/status`, { status }),
};
```

### 8-5. UI 레이아웃 (Linear 스타일)

```
┌─────────────────────────────────────────────────────────┐
│  Agentic Worker          [+ New Project]                │
├──────────────┬──────────────────────────────────────────┤
│ Projects     │  chat-platform              [+ New Issue]│
│ ─────────    │  ──────────────────────────────────────  │
│ chat-platform│  #1  Add rate limiting    HIGH  OPEN     │
│ api-gateway  │  #2  Fix auth bug         CRIT  IN_PROG  │
│              │  #3  Refactor service     LOW   OPEN     │
└──────────────┴──────────────────────────────────────────┘
```

---

## 9. 디렉토리 구조 (전체)

```
src/main/java/com/example/worker/
├── project/
│   ├── domain/
│   │   └── model/
│   │       ├── Project.java
│   │       ├── ProjectId.java
│   │       ├── LocalPath.java
│   │       └── BranchName.java
│   ├── application/
│   │   ├── service/
│   │   │   ├── ProjectCommandService.java
│   │   │   └── ProjectQueryService.java
│   │   ├── dto/
│   │   │   ├── ProjectSummary.java
│   │   │   └── ProjectDetail.java
│   │   └── port/
│   │       └── ProjectRepository.java       ← 도메인 포트 인터페이스
│   ├── infrastructure/
│   │   └── datasource/
│   │       ├── ProjectJpaEntity.java
│   │       ├── ProjectJpaRepository.java    ← Spring Data JPA
│   │       └── ProjectRepositoryAdapter.java ← ProjectRepository 구현
│   └── api/
│       ├── controller/
│       │   └── ProjectController.java
│       ├── request/
│       │   └── CreateProjectRequest.java
│       └── response/
│           └── ProjectResponse.java
│
├── issue/
│   ├── domain/
│   │   └── model/
│   │       ├── Issue.java
│   │       ├── IssueId.java
│   │       ├── IssueNumber.java
│   │       ├── IssueStatus.java             ← Enum
│   │       └── Priority.java               ← Enum
│   ├── application/
│   │   ├── service/
│   │   │   ├── IssueCommandService.java
│   │   │   ├── IssueQueryService.java
│   │   │   └── IssueNumberSequenceService.java
│   │   ├── dto/
│   │   │   └── IssueSummary.java
│   │   └── port/
│   │       └── IssueRepository.java
│   ├── event/
│   │   └── model/
│   │       └── IssueCreatedEvent.java       ← @Externalized("issue-created")
│   ├── infrastructure/
│   │   └── datasource/
│   │       ├── IssueJpaEntity.java
│   │       ├── IssueJpaRepository.java
│   │       └── IssueRepositoryAdapter.java
│   └── api/
│       ├── controller/
│       │   └── IssueController.java
│       ├── request/
│       │   ├── CreateIssueRequest.java
│       │   └── UpdateIssueStatusRequest.java
│       └── response/
│           └── IssueResponse.java
│
└── agent/
    ├── domain/
    │   └── model/
    │       ├── AgentJob.java                ← skeleton (다음 단계)
    │       └── AgentJobStatus.java
    ├── application/
    │   └── service/
    │       └── AgentWorkerService.java      ← skeleton
    └── infrastructure/
        └── kafka/
            └── IssueCreatedEventConsumer.java

src/main/resources/
├── static/
│   ├── index.html
│   ├── css/app.css
│   └── js/
│       ├── app.js
│       ├── router.js
│       ├── api.js
│       └── components/
│           ├── ProjectList.js
│           ├── ProjectForm.js
│           ├── IssueList.js
│           ├── IssueForm.js
│           └── IssueDetail.js
├── db/migration/
│   └── V1__init_schema.sql
└── application.yml
```

---

## 10. application.yml

```yaml
server:
  port: 18081

spring:
  application:
    name: agentic-worker

  datasource:
    url: jdbc:postgresql://localhost:5432/agentic_worker
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate          # Flyway가 스키마 관리
    open-in-view: false

  flyway:
    locations: classpath:db/migration

  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: agent-worker
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.worker.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    listener:
      concurrency: 1              # 직렬 처리 필수

  modulith:
    events:
      kafka:
        enable: true

logging:
  level:
    com.example.worker: DEBUG
```

---

## 11. build.gradle 변경 사항

```groovy
// 제거 (Thymeleaf 불필요)
// implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'

// 추가 (Flyway DB 마이그레이션)
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

---

## 12. 시퀀스 다이어그램

### 12-1. 프로젝트 등록

```
Browser(Vue)          ProjectController     ProjectCommandService   ProjectRepository
    │                        │                       │                      │
    │  POST /api/projects    │                       │                      │
    │───────────────────────►│                       │                      │
    │                        │  registerProject()    │                      │
    │                        │──────────────────────►│                      │
    │                        │                       │  LocalPath 검증       │
    │                        │                       │  Project.create()    │
    │                        │                       │  save(project)       │
    │                        │                       │─────────────────────►│
    │                        │                       │◄─────────────────────│
    │                        │  ProjectResponse      │                      │
    │◄───────────────────────│                       │                      │
    │  201 Created           │                       │                      │
```

### 12-2. 이슈 생성 → Kafka 이벤트

```
Browser(Vue)    IssueController   IssueCommandService   IssueRepo   EventPublisher   Kafka
    │                │                    │                 │              │            │
    │ POST /api/     │                    │                 │              │            │
    │ projects/{id}  │                    │                 │              │            │
    │ /issues        │                    │                 │              │            │
    │───────────────►│                    │                 │              │            │
    │                │  createIssue()     │                 │              │            │
    │                │───────────────────►│                 │              │            │
    │                │                   │ nextNumber()     │              │            │
    │                │                   │─────────────────►│              │            │
    │                │                   │ Issue.create()   │              │            │
    │                │                   │ save(issue)      │              │            │
    │                │                   │─────────────────►│              │            │
    │                │                   │ publishEvent(    │              │            │
    │                │                   │  IssueCreated)   │              │            │
    │                │                   │────────────────────────────────►│            │
    │                │  IssueResponse    │                 [트랜잭션 커밋 후]│            │
    │◄───────────────│                   │                                 │ produce    │
    │  201 Created   │                   │                                 │───────────►│
    │                │                   │                                 │            │
    │                │              IssueCreatedEventConsumer               │            │
    │                │                   │                           consume│◄───────────│
    │                │                   │                           log.info(...)      │
```

---

## 13. 구현 순서 체크리스트

### Step 1 — 인프라 설정
- [ ] `build.gradle` 수정 (Thymeleaf 제거, Flyway 추가)
- [ ] `application.yml` 작성
- [ ] `V1__init_schema.sql` 작성
- [ ] `GlobalExceptionHandler` 작성

### Step 2 — project BC
- [ ] `Project`, `ProjectId`, `LocalPath`, `BranchName` (domain/model)
- [ ] `ProjectRepository` 포트 인터페이스
- [ ] `ProjectCommandService`, `ProjectQueryService`
- [ ] `ProjectJpaEntity`, `ProjectJpaRepository`, `ProjectRepositoryAdapter`
- [ ] `ProjectController`, `CreateProjectRequest`, `ProjectResponse`

### Step 3 — issue BC
- [ ] `Issue`, `IssueId`, `IssueNumber`, `IssueStatus`, `Priority` (domain/model)
- [ ] `IssueRepository` 포트 인터페이스
- [ ] `IssueNumberSequenceService`
- [ ] `IssueCommandService`, `IssueQueryService`
- [ ] `IssueCreatedEvent` (`@Externalized`)
- [ ] `IssueJpaEntity`, `IssueJpaRepository`, `IssueRepositoryAdapter`
- [ ] `IssueController`, Request/Response records

### Step 4 — agent BC (Consumer skeleton)
- [ ] `IssueCreatedEventConsumer` (`@KafkaListener` — 로깅만)
- [ ] `AgentJob`, `AgentJobStatus` skeleton

### Step 5 — UI (Vue 3 CDN)
- [ ] `index.html` (CDN 스크립트 로드)
- [ ] `api.js` (ProjectApi, IssueApi)
- [ ] `router.js`
- [ ] `ProjectList.js`, `ProjectForm.js`
- [ ] `IssueList.js`, `IssueForm.js`, `IssueDetail.js`
- [ ] `app.css` (기본 스타일)
