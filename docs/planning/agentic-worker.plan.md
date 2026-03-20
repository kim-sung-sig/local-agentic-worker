# [Plan] Agentic Worker — 자율 개발 자동화 시스템

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agentic-worker |
| 작성일 | 2026-03-20 |
| 상태 | Plan |
| 기술 스택 | Java 21 + Spring Boot 3.5.12 + PostgreSQL + Kafka + Spring Modulith |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | 이슈 생성 후 브랜치 생성, 코드 작성, PR 제출까지 개발자가 수동으로 반복 처리해야 하는 병목 |
| **Solution** | 자체 이슈 트래커(Jira 유사) + Kafka 이벤트 + 로컬 Claude CLI 자동 실행 → Draft PR 파이프라인 |
| **Function UX Effect** | 개발자는 이슈 작성 + 최종 PR 리뷰만 담당; 중간 구현은 AI 에이전트가 자율 처리 |
| **Core Value** | 서버 비용 Zero, Claude Pro $20 구독으로 운영, 외부 SaaS 의존 없는 완전 로컬 자동화 |

---

## 1. Problem Statement

### 1-1. 문제

개발자가 이슈를 생성한 이후 다음 작업들을 수동으로 처리해야 한다:
- Git 브랜치 생성
- 이슈 설명 기반 코드 구현
- 빌드 / 테스트 검증
- Draft PR 제출

이 반복 작업은 단순하지만 컨텍스트 전환 비용이 크다.

### 1-2. 왜 지금?

- Claude CLI(`claude --print --dangerously-skip-permissions`)가 로컬 파일시스템 작업 자동화에 충분한 수준에 도달했다.
- Notion 외부 서비스 없이 자체 이슈 트래커로 완전 로컬 파이프라인 구성이 가능하다.

---

## 2. Goals / Non-Goals

### Goals (이번 단계 범위)

- [ ] Git Repository를 **Project**로 등록 (baseBranch 지정)
- [ ] 등록된 Project에 **Issue** 발행 (title, description, priority)
- [ ] Project·Issue 데이터를 로컬 PostgreSQL에 저장
- [ ] Issue 생성 시 Kafka `issue-created` 이벤트 발행
- [ ] Kafka Consumer가 이벤트를 수신하여 에이전트 워크플로우 진입점 확보
- [ ] 기본 UI (Thymeleaf — 이슈 목록, 프로젝트 목록)

### Non-Goals (이번 단계 제외)

- Claude CLI 실행 / 브랜치 생성 / PR 생성 (다음 단계)
- Notion 연동 (완전 제거)
- 인증 / 권한 관리
- 멀티 유저

---

## 3. Domain Knowledge

### 핵심 개념

| 용어 | 정의 |
|------|------|
| **Project** | 자동화 대상 Git 레포지토리 단위. localPath + baseBranch 포함 |
| **Issue** | Project 하위 작업 단위. 에이전트 실행의 트리거 |
| **Agent** | Claude CLI를 프로세스로 실행하여 코드를 작성·검증·PR하는 자율 프로세스 |
| **baseBranch** | PR 대상이 되는 기준 브랜치 (예: `main`, `develop`) |
| **issueBranch** | Agent가 생성하는 작업 브랜치 (`feat/issue-{number}-{slug}`) |

### 도메인 규칙

- Project는 `localPath`가 유일해야 한다 (중복 등록 불가).
- Issue는 반드시 존재하는 Project에 속해야 한다.
- Issue 상태 전이: `OPEN` → `IN_PROGRESS` → `IN_REVIEW` | `FAILED` → `CLOSED`
- Kafka concurrency = 1 (직렬 처리 — 동일 레포 파일 충돌 방지).

---

## 4. Requirements

### 4-1. Functional Requirements

**[Project 관리]**
- `POST /projects` — 프로젝트 등록 (name, localPath, baseBranch)
- `GET /projects` — 프로젝트 목록 조회
- `GET /projects/{id}` — 프로젝트 상세
- 로컬 경로 유효성 검증 (디렉토리 존재 여부)

**[Issue 관리]**
- `POST /projects/{projectId}/issues` — 이슈 생성 (title, description, priority)
- `GET /projects/{projectId}/issues` — 이슈 목록 조회
- `GET /issues/{id}` — 이슈 상세
- `PATCH /issues/{id}/status` — 상태 변경

**[이벤트]**
- Issue 생성 시 Spring Modulith ApplicationEvent 발행 → Kafka `issue-created` 토픽 produce
- `TicketCreatedEventConsumer` → 이벤트 수신 확인 및 로깅 (Claude 실행은 다음 단계)

**[UI]**
- Thymeleaf 기반 프로젝트/이슈 보드 (단순 목록, Linear 스타일)

### 4-2. Non-Functional Requirements

- 로컬 PostgreSQL (Docker `localhost:5432`)
- Kafka (Docker `localhost:29092`)
- Spring Modulith 모듈 경계 준수
- DDD 레이어 (`domain/` → `application/` → `api/` + `infrastructure/`)

---

## 5. Domain Model & Boundaries

### Bounded Contexts

```
┌──────────────────────────────────────────────────────────┐
│  project (BC)            issue (BC)         agent (BC)   │
│  ─────────────           ──────────         ──────────   │
│  Project                 Issue              AgentJob      │
│  - id                    - id               - id          │
│  - name                  - projectId        - issueId     │
│  - localPath             - title            - status      │
│  - baseBranch            - description      - branchName  │
│  - createdAt             - priority         - startedAt   │
│                          - status           - finishedAt  │
│                          - issueNumber      - errorMsg    │
│                          - createdAt                      │
└──────────────────────────────────────────────────────────┘
```

### 이벤트 흐름

```
[POST /projects/{id}/issues]
        ↓
[Issue Domain — issueNumber 채번]
        ↓
[ApplicationEvent: IssueCreatedEvent]        ← Spring Modulith
        ↓
[Kafka produce: issue-created topic]         ← infrastructure/kafka
        ↓
[IssueCreatedEventConsumer]                  ← agent BC 진입점
        ↓ (이번 단계: 로깅만)
        ↓ (다음 단계: GitBranchService → ClaudeAgentExecutor → PullRequestService)
```

---

## 6. Data & Interfaces

### DB 스키마 (주요 테이블)

```sql
-- project
CREATE TABLE project (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    local_path  VARCHAR(500) NOT NULL UNIQUE,
    base_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    created_at  TIMESTAMP NOT NULL
);

-- issue
CREATE TABLE issue (
    id           UUID PRIMARY KEY,
    project_id   UUID NOT NULL REFERENCES project(id),
    issue_number INTEGER NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    priority     VARCHAR(20) NOT NULL,  -- LOW / MEDIUM / HIGH / CRITICAL
    status       VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMP NOT NULL,
    UNIQUE(project_id, issue_number)
);
```

### Kafka 이벤트 페이로드

```json
// topic: issue-created
{
  "issueId": "uuid",
  "issueNumber": 42,
  "title": "Add rate limiting to voice endpoint",
  "description": "...",
  "priority": "HIGH",
  "projectId": "uuid",
  "projectLocalPath": "/c/git/chat-platform",
  "baseBranch": "main",
  "occurredAt": "2026-03-20T10:00:00Z"
}
```

### REST API 요약

| Method | Path | 설명 |
|--------|------|------|
| POST | `/projects` | 프로젝트 등록 |
| GET | `/projects` | 프로젝트 목록 |
| GET | `/projects/{id}` | 프로젝트 상세 |
| POST | `/projects/{id}/issues` | 이슈 생성 |
| GET | `/projects/{id}/issues` | 이슈 목록 |
| GET | `/issues/{id}` | 이슈 상세 |
| PATCH | `/issues/{id}/status` | 상태 변경 |

---

## 7. 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.5.12 |
| 아키텍처 | Spring Modulith (DDD, 모듈 경계) |
| UI | Thymeleaf + HTMX |
| DB | PostgreSQL (로컬 Docker) + Spring Data JPA |
| 메시지 브로커 | Apache Kafka (로컬 Docker `localhost:29092`) |
| 빌드 | Gradle |
| AI 에이전트 | Claude CLI (다음 단계) |

---

## 8. 구현 단계 (이번 범위)

### Phase 1 — Project 관리
- [ ] `project` BC: `Project` 도메인 모델, Repository
- [ ] `ProjectCommandService` / `ProjectQueryService`
- [ ] `ProjectController` (REST)
- [ ] `ProjectRepository` (JPA)
- [ ] Thymeleaf 프로젝트 목록/등록 UI

### Phase 2 — Issue 관리
- [ ] `issue` BC: `Issue` 도메인 모델, issueNumber 채번 로직
- [ ] `IssueCommandService` / `IssueQueryService`
- [ ] `IssueController` (REST)
- [ ] `IssueRepository` (JPA)
- [ ] Thymeleaf 이슈 목록/생성 UI

### Phase 3 — 이벤트 파이프라인
- [ ] `IssueCreatedEvent` (Spring Modulith 이벤트 페이로드)
- [ ] Kafka produce — `IssueCreatedEventPublisher`
- [ ] Kafka consume — `IssueCreatedEventConsumer` (로깅 + `agent` BC 진입점)

---

## 9. 위험 요소 및 대응

| 위험 | 대응 |
|------|------|
| Kafka concurrency > 1 → 동일 레포 파일 충돌 | `concurrency: 1` 강제, 설정 주석 명시 |
| localPath 잘못된 경로 등록 | 등록 시 `Files.isDirectory()` 검증 |
| Spring Modulith 이벤트 vs Kafka 이중 발행 | Modulith `@ApplicationModuleListener` → Kafka produce 1회만 |
| PostgreSQL 미실행 | `application.yml`에 실패 시 안내 메시지 |

---

## 10. 다음 단계 (다음 Phase 범위)

- `/pdca design agentic-worker` — 상세 설계 문서 (클래스 다이어그램, 시퀀스)
- Phase 4: `agent` BC — `GitBranchService` + `ClaudeAgentExecutor` + `PullRequestService`
- Phase 5: Issue 상태 자동 업데이트 (IN_PROGRESS / IN_REVIEW / FAILED)
- Phase 6: DLT 처리 + 재시도 로직

---

## 11. Open Questions

- Thymeleaf 대신 REST + 별도 프론트엔드(React 등)로 분리할 것인가?
- issueNumber는 프로젝트 내 순번(1, 2, 3...)인가, 전역 순번인가? → **프로젝트 내 순번**으로 결정
- 로컬 PostgreSQL Docker compose 파일을 이 레포에 포함할 것인가?
