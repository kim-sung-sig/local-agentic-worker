# [Plan] Agent Worker

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | notion-agent-worker |
| 작성일 | 2026-03-20 |
| 상태 | Plan |
| 구현 위치 | 별도 독립 프로젝트 (이 저장소 외부) |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | 티켓 생성 후 브랜치 생성, 코드 작성, PR 제출까지 개발자가 수동으로 처리해야 하는 반복 작업 |
| **Solution** | 티켓 생성 → Kafka 이벤트 → 로컬 Claude CLI 자동 실행 → Draft PR 자동 생성 파이프라인 |
| **Function UX Effect** | 개발자는 티켓 작성 + 최종 PR 리뷰만 담당, 중간 구현 작업은 AI 에이전트가 자율 처리 |
| **Core Value** | 서버 비용 Zero, Claude Pro $20 구독으로 운영, GitHub Actions 불필요한 완전 로컬 자동화 |

---

## 1. 목표 및 배경

### 1-1. 목적

티켓 생성 시 AI 에이전트(Claude CLI)가 자율적으로:
1. Git 브랜치 자동 생성
2. 티켓 설명 기반 코드 구현
3. 빌드/테스트 자동 검증
4. Draft PR 자동 제출

개발자는 **티켓 작성**과 **PR 최종 리뷰**만 담당한다.

### 1-2. 배경 및 제약

| 제약 | 내용 |
|------|------|
| 서버 비용 | 없음 → 로컬 PC에서 모든 작업 처리 |
| 공개 webhook 수신 | 불가 → GitHub Actions 대신 로컬 Kafka 사용 |
| 기술 스택 제한 | Java/Spring Boot 기반 (Node.js 미사용) |
| Claude 요금 | Pro $20/월 구독 활용 (API 종량제 없음) |

---

## 2. 시스템 구성

### 2-1. 전체 컴포넌트

```
[브라우저 UI]
    ↓ HTTP
[Notion Wrapper Server]  ← Spring Boot (로컬 실행)
    ↓ Notion REST API
[Notion Database]        ← 티켓 저장소
    ↓ Kafka Produce
[Local Kafka]            ← Docker (localhost:29092)
    ↓ Kafka Consume
[Agent Worker]           ← Spring Boot Consumer (로컬 실행)
    ↓ ProcessBuilder
[claude CLI]             ← Pro 구독, 로컬 파일시스템 직접 접근
    ↓ git push + gh pr
[GitHub Remote]          ← Draft PR 생성
```

### 2-2. 서비스 포트 계획

| 서비스 | 포트 | 역할 |
|--------|------|------|
| Agent Worker | 18081 | Kafka Consumer + Claude 실행 |
| Kafka | 29092 | 로컬 이벤트 브로커 (기존 Docker) |

---

## 3. 주요 기능 명세

### 3-1. Notion Wrapper Server

**기능 목록**
- `POST /tickets` — 티켓 생성 (Notion 페이지 생성 + Kafka 이벤트 발행)
- `GET /tickets` — 티켓 목록 조회 (Notion DB 쿼리)
- `GET /tickets/{id}` — 티켓 상세 조회
- `PATCH /tickets/{id}/status` — 상태 변경 (Todo / In Progress / Done)
- UI: Thymeleaf 기반 티켓 보드 (Linear 스타일 단순화)

**Notion 연동**
- Notion API v1 (`https://api.notion.com/v1`)
- Integration Token으로 인증
- Database ID로 티켓 페이지 CRUD

**Kafka 이벤트 발행 (ticket-created)**
```json
{
  "ticketNumber": 42,
  "title": "Add rate limiting to voice endpoint",
  "description": "VoiceWebSocketConfig의 /ws/voice에 연결 수 제한 필요...",
  "notionPageId": "abc-123-def",
  "priority": "HIGH",
  "targetRepo": "/c/git/chat-application/chat-platform",
  "baseBranch": "main",
  "createdAt": "2026-03-20T10:00:00Z"
}
```

### 3-2. Agent Worker

**Kafka Consumer**
- 토픽: `ticket-created`
- 동시성: `concurrency: 1` (직렬 처리 — 파일 충돌 방지)
- 오류 시: DLT(Dead Letter Topic) `ticket-created.DLT`로 이동

**브랜치 생성 로직**
```
브랜치명 형식: feat/issue-{ticketNumber}-{kebab-case-title}
예시: feat/issue-42-add-rate-limiting-to-voice-endpoint
```

**Claude CLI 실행 (ProcessBuilder)**
```
claude \
  --print \
  --dangerously-skip-permissions \
  --max-turns 20 \
  "{프롬프트}"
```

**프롬프트 구성 요소**
1. CLAUDE.md 및 CONVENTIONS.md 읽기 지시
2. 티켓 번호, 제목, 상세 설명
3. 브랜치명 (이미 체크아웃됨)
4. 빌드/테스트 명령어
5. Draft PR 생성 지시

**PR 생성**
```bash
gh pr create \
  --draft \
  --title "feat: {title} (#{ticketNumber})" \
  --body "Closes #{ticketNumber}\n\n{Claude 구현 요약}" \
  --base main
```

### 3-3. 상태 알림

Claude 작업 완료/실패 시 Notion 티켓 상태 업데이트:

| 이벤트 | Notion 상태 |
|--------|------------|
| Agent Worker 수신 | `In Progress` |
| Claude 완료 + PR 생성 | `In Review` |
| 빌드/테스트 실패 | `Failed` (댓글에 오류 내용 추가) |

---

## 4. 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.4.x |
| UI | Thymeleaf + HTMX (서버사이드 렌더링) |
| 메시지 브로커 | Apache Kafka (기존 로컬 Docker 활용) |
| 티켓 저장소 | Notion Database (Notion API v1) |
| AI 에이전트 | Claude CLI (`claude` 바이너리, Pro 구독) |
| Git 자동화 | JGit 또는 ProcessBuilder(git CLI) |
| PR 생성 | GitHub CLI (`gh`) |
| 빌드 도구 | Gradle |

---

## 5. 구현 단계 (로드맵)

### Phase 1 — Notion 연동 기반 (1~2일)
- [ ] Spring Boot 프로젝트 생성 (별도 폴더)
- [ ] Notion API Client 구현 (`NotionApiClient`)
- [ ] 티켓 CRUD REST API (`TicketController`)
- [ ] Thymeleaf 티켓 목록/상세 UI
- [ ] `POST /tickets` 시 Kafka `ticket-created` 발행

### Phase 2 — Agent Worker (1~2일)
- [ ] Kafka Consumer 구현 (`TicketCreatedEventConsumer`)
- [ ] 브랜치 생성 로직 (`GitBranchService`)
- [ ] Claude CLI ProcessBuilder 실행 (`ClaudeAgentExecutor`)
- [ ] 프롬프트 빌더 (`AgentPromptBuilder`)
- [ ] PR 생성 (`PullRequestService`)

### Phase 3 — 안정화 (1일)
- [ ] Notion 상태 업데이트 (In Progress / In Review / Failed)
- [ ] DLT 처리 (실패 티켓 재처리 또는 알림)
- [ ] 직렬 처리 보장 (`concurrency: 1`)
- [ ] Claude 실행 타임아웃 설정 (30분)

---

## 6. 위험 요소 및 대응

| 위험 | 대응 |
|------|------|
| PC 절전 모드 진입 시 작업 중단 | OS 절전 방지 설정 or 작업 중 알림 |
| 동시 티켓 2개로 파일 충돌 | Kafka `concurrency: 1` 직렬 처리 |
| Claude 무한 루프 | `--max-turns 20` + 타임아웃 30분 |
| 빌드 실패 반복 | 최대 3회 재시도 후 DLT 이동 |
| `--dangerously-skip-permissions` 오남용 | `targetRepo` 경로만 작업 디렉토리로 설정 |
| Notion API Rate Limit | 429 시 지수 백오프 재시도 |

---

## 7. 디렉토리 구조 (별도 프로젝트)

```
notion-agent-worker/              ← 새 독립 프로젝트
├── src/main/java/com/example/agent/
│   ├── ticket/
│   │   ├── domain/model/         ← Ticket, TicketStatus (순수 Java)
│   │   ├── application/service/  ← TicketCommandService, TicketQueryService
│   │   ├── infrastructure/notion/ ← NotionApiClient (외부 어댑터)
│   │   ├── infrastructure/kafka/ ← TicketCreatedEventPublisher
│   │   └── api/controller/       ← TicketController
│   └── agent/
│       ├── application/service/  ← AgentWorkerService, ClaudeAgentExecutor
│       ├── infrastructure/kafka/ ← TicketCreatedEventConsumer
│       ├── infrastructure/git/   ← GitBranchService
│       └── infrastructure/gh/    ← PullRequestService
├── src/main/resources/
│   ├── application.yml
│   └── templates/                ← Thymeleaf 템플릿
└── build.gradle
```

---

## 8. 설정 항목 (application.yml)

```yaml
notion:
  api-key: ${NOTION_API_KEY}
  database-id: ${NOTION_DATABASE_ID}

agent:
  target-repo: C:/git/chat-application/chat-platform
  base-branch: main
  max-turns: 20
  timeout-minutes: 30
  claude-path: claude  # PATH에 등록된 claude CLI

spring:
  kafka:
    bootstrap-servers: localhost:29092
    listener:
      concurrency: 1  # 직렬 처리 필수
```

---

## 9. 다음 단계

- `/pdca design notion-agent-worker` — 상세 설계 문서 작성
- 별도 폴더에서 프로젝트 초기화: `spring init notion-agent-worker`
- Notion Integration 생성: `https://www.notion.so/my-integrations`
- Notion Database 템플릿 구성 (티켓 속성: Title, Status, Priority, Description)
