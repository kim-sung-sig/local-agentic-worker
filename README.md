# Agentic Worker

> **이슈 기반 자율 개발 자동화 시스템**
> Issue Tracker(Jira 유사) + Kafka 이벤트 + Claude CLI 에이전트 파이프라인

---

## 개요

Agentic Worker는 개발자가 이슈를 생성하면 AI 에이전트가 자율적으로 코드를 구현하고 Draft PR을 제출하는 **완전 로컬 개발 자동화 플랫폼**이다.

외부 SaaS(Notion, Jira 등)에 의존하지 않고, 자체 이슈 트래커와 로컬 Kafka를 통해 파이프라인을 구성한다.

```
[개발자: 이슈 작성]
        ↓
[Agentic Worker — Issue Tracker]
        ↓ Kafka event: issue-created
[Agent Worker — Claude CLI 실행]
        ↓ git branch + 코드 구현 + 빌드 검증
[GitHub — Draft PR 자동 생성]
        ↓
[개발자: PR 리뷰만 담당]
```

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.5.12 |
| 아키텍처 | Spring Modulith (DDD Bounded Context) |
| DB | PostgreSQL (로컬 Docker) |
| 메시지 브로커 | Apache Kafka (로컬 Docker) |
| UI | Thymeleaf + HTMX |
| AI 에이전트 | Claude CLI (`claude` — Pro 구독) |
| 빌드 | Gradle |

---

## 핵심 기능

### 현재 구현 범위
- **Project 등록**: Git 레포지토리를 프로젝트로 등록 (localPath + baseBranch 지정)
- **Issue 발행**: 등록된 프로젝트에 이슈 생성 (title, description, priority)
- **로컬 DB 저장**: Project·Issue 데이터를 PostgreSQL에 영속화
- **Kafka 이벤트**: 이슈 생성 시 `issue-created` 이벤트 발행 → Consumer 수신

### 다음 구현 범위
- Agent 실행: Git 브랜치 생성 → Claude CLI 프로세스 실행 → Draft PR 생성
- Issue 상태 자동 업데이트 (IN_PROGRESS / IN_REVIEW / FAILED)
- DLT(Dead Letter Topic) 처리

---

## 아키텍처

Spring Modulith 기반 DDD — 3개 Bounded Context:

```
com.example.worker
├── project/          ← Project BC (등록, 조회)
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── api/
├── issue/            ← Issue BC (발행, 상태 관리)
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── api/
└── agent/            ← Agent BC (Kafka consumer, Claude 실행)
    ├── domain/
    ├── application/
    ├── infrastructure/
    └── api/
```

레이어 의존 방향: `api/` → `application/` → `domain/` ← `infrastructure/`

---

## 시작하기

### 사전 요구사항

- Java 21
- Docker (PostgreSQL + Kafka)
- Claude CLI (`claude` — Pro 구독, PATH 등록)
- GitHub CLI (`gh`)

### 로컬 인프라 실행

```bash
# PostgreSQL + Kafka (Docker Compose)
docker compose up -d
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 포트: `http://localhost:18081`

---

## 설정

`src/main/resources/application.yml`:

```yaml
server:
  port: 18081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentic_worker
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  kafka:
    bootstrap-servers: localhost:29092
    listener:
      concurrency: 1   # 직렬 처리 필수 (파일 충돌 방지)
```

---

## 문서

| 문서 | 경로 |
|------|------|
| Plan | [docs/planning/agentic-worker.plan.md](docs/planning/agentic-worker.plan.md) |
| Conventions | [docs/conventions/CONVENTIONS.md](docs/conventions/CONVENTIONS.md) |
| Claude 설정 | [CLAUDE.md](CLAUDE.md) |
