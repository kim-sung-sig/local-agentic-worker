# [Design] Agent Executor 개선 — Phase 2: SSE 스트리밍 + 승인/반려 피드백 루프

## Executive Summary

| 관점 | 내용 |
|------|------|
| **Problem** | AgentJob 실행 중 진행상황을 실시간으로 볼 수 없고, PR 생성 후 승인/반려 피드백 루프가 없음 |
| **Solution** | `claude --output-format stream-json` 전환 + SSE 엔드포인트로 실시간 로그 스트리밍, 승인/반려 API + 피드백 재시도 루프 구현 |
| **Function UX Effect** | IssueDetail 화면에서 에이전트 작업 로그를 실시간 확인, IN_REVIEW 상태에서 승인/반려 버튼으로 Human-in-the-Loop 제어 |
| **Core Value** | 블랙박스 자동화에서 투명하고 통제 가능한 AI 개발 파이프라인으로 전환 |

---

## 1. 변경 범위

### 1.1 신규 파일

| 레이어 | 파일 | 역할 |
|--------|------|------|
| `agent/domain/model/` | `AgentLog.java` | 에이전트 로그 값 객체 (불변 record) |
| `agent/application/port/` | `AgentLogStore.java` | 로그 저장소 인터페이스 |
| `agent/infrastructure/stream/` | `InMemoryAgentLogStore.java` | ConcurrentHashMap 기반 구현체 |
| `agent/api/controller/` | `AgentJobStreamController.java` | SSE 스트리밍 엔드포인트 |
| `issue/api/request/` | `ReviewIssueRequest.java` | 승인/반려 요청 DTO (record) |
| `issue/api/controller/` | `IssueReviewController.java` | 승인/반려 API 엔드포인트 |
| `issue/application/service/` | `IssueReviewService.java` | 승인/반려 비즈니스 로직 |
| `issue/event/model/` | `IssueRejectedEvent.java` | 반려 이벤트 페이로드 |

### 1.2 수정 파일

| 파일 | 변경 내용 |
|------|-----------|
| `ClaudeAgentExecutor.java` | `--output-format stream-json` 전환, 라인별 파싱 후 `AgentLogStore` 저장 |
| `AgentWorkerService.java` | `startPlanning()/startCoding()/startVerifying()` 호출 시점 추가, `IssueRejectedEvent` 리스너 추가 |
| `PromptBuilder.java` | 재시도 시 피드백 히스토리 포함 |
| `IssueStatus.java` | `REJECTED` 상태 추가 |
| `IssueDetail.vue` | SSE 구독 + 로그 패널 + 승인/반려 버튼 |
| `api/index.js` | AgentJob 스트림, 리뷰 API 추가 |

---

## 2. 도메인 설계

### 2.1 AgentLog (값 객체)

```java
// agent/domain/model/AgentLog.java
public record AgentLog(
    AgentJobId jobId,
    Instant timestamp,
    LogType type,      // TEXT, TOOL_USE, TOOL_RESULT, STATUS_CHANGE
    String content
) {
    public static AgentLog text(AgentJobId jobId, String text) { ... }
    public static AgentLog toolUse(AgentJobId jobId, String toolName, String input) { ... }
    public static AgentLog statusChange(AgentJobId jobId, AgentJobStatus status) { ... }
}

public enum LogType { TEXT, TOOL_USE, TOOL_RESULT, STATUS_CHANGE }
```

### 2.2 AgentLogStore (포트)

```java
// agent/application/port/AgentLogStore.java
public interface AgentLogStore {
    void append(AgentLog log);
    List<AgentLog> findByJobId(AgentJobId jobId);
    void registerSink(AgentJobId jobId, Consumer<AgentLog> sink);
    void unregisterSink(AgentJobId jobId);
}
```

### 2.3 IssueStatus 확장

```java
// 추가: REJECTED
public enum IssueStatus {
    OPEN, IN_PROGRESS, IN_REVIEW, REJECTED, FAILED, CLOSED
}
```

### 2.4 IssueRejectedEvent

```java
// issue/event/model/IssueRejectedEvent.java
public record IssueRejectedEvent(
    UUID issueId,
    String feedback,
    int retryCount
) {}
```

---

## 3. 애플리케이션 서비스 설계

### 3.1 ClaudeAgentExecutor — stream-json 전환

```
claude --output-format stream-json --allowedTools "{tools}" -p "{prompt}"
```

stdout에서 NDJSON 라인 파싱:
```
{"type":"assistant","message":{"content":[{"type":"text","text":"..."}]}}
{"type":"tool_use","name":"Write","input":{...}}
{"type":"result","subtype":"success","result":"..."}
```

파싱 로직:
```java
// 각 라인 → AgentLog 변환 → AgentLogStore.append()
// type="assistant" + content[0].type="text" → LogType.TEXT
// type="tool_use"                            → LogType.TOOL_USE
// type="result"                              → LogType.TEXT (최종 요약)
```

최종 반환값: `result` 타입 라인의 `result` 필드 값 (기존 호환 유지)

### 3.2 AgentWorkerService — 상태 전환 시점

```
handle() 진입         → job.startPlanning()  → save
claude 실행 직전      → job.startCoding()    → save
claude 실행 완료 후   → job.startVerifying() → save (push/PR 생성 전)
PR 생성 완료          → job.complete(prUrl)  → save
```

### 3.3 IssueReviewService

```java
// 승인
void approve(UUID issueId) {
    issue.transitionTo(IssueStatus.CLOSED);
    // PR ready-for-review 전환 (gh pr ready {prUrl})
    issueRepository.save(issue);
}

// 반려
void reject(UUID issueId, String feedback) {
    issue.transitionTo(IssueStatus.REJECTED);
    issueRepository.save(issue);
    eventPublisher.publishEvent(new IssueRejectedEvent(issueId, feedback, retryCount + 1));
}
```

### 3.4 AgentWorkerService — 반려 재시도

```java
@EventListener
void handleRejected(IssueRejectedEvent event) {
    // 기존 AgentJob 히스토리에서 피드백 수집
    // PromptBuilder.buildRetry(event, feedbackHistory)로 프롬프트 재생성
    // 새 AgentJob 생성 후 handle() 재실행
}
```

---

## 4. API 설계

### 4.1 SSE 스트리밍 엔드포인트

```
GET /api/agent-jobs/{jobId}/stream
Content-Type: text/event-stream

data: {"type":"STATUS_CHANGE","content":"PLANNING","timestamp":"..."}
data: {"type":"TEXT","content":"이슈 분석 중...","timestamp":"..."}
data: {"type":"TOOL_USE","content":"Write: src/main/java/...","timestamp":"..."}
data: {"type":"STATUS_CHANGE","content":"CODING","timestamp":"..."}
...
data: {"type":"STATUS_CHANGE","content":"SUCCEEDED","timestamp":"..."}
```

완료/실패 시 `event: done` 전송 후 연결 종료.

### 4.2 AgentJob 조회 (기존 확장)

```
GET /api/issues/{issueId}/agent-job
Response: { id, status, branchName, prUrl, startedAt, finishedAt }
```

### 4.3 리뷰 API

```
POST /api/issues/{issueId}/review
Body:  { "approved": true }
Body:  { "approved": false, "feedback": "테스트 코드 누락" }

Response 200: { "status": "CLOSED" | "REJECTED" }
Response 409: 이미 처리된 이슈
```

---

## 5. 프론트엔드 설계

### 5.1 IssueDetail.vue 확장

```
[기존 메타 정보 + 상태 배지]

[에이전트 작업 패널] — IN_PROGRESS / IN_REVIEW일 때 표시
  ┌─────────────────────────────────────────┐
  │ 진행 상태: CODING                        │
  │ ─────────────────────────────────────── │
  │ [11:23:01] 이슈 분석 중...               │
  │ [11:23:05] Write: src/main/.../Foo.java  │
  │ [11:23:12] Edit: src/test/.../FooTest.java│
  │ [11:23:20] ./gradlew test 실행 중...     │
  └─────────────────────────────────────────┘

[리뷰 패널] — IN_REVIEW 상태일 때만 표시
  PR: https://github.com/...  [열기]
  ┌─────────────────────┐  ┌────────────────────┐
  │     승인            │  │       반려          │
  └─────────────────────┘  └────────────────────┘
  [반려 시 피드백 텍스트 입력창 노출]
```

### 5.2 SSE 연결 생명주기

```javascript
// IN_PROGRESS 진입 시 연결
const es = new EventSource(`/api/agent-jobs/${jobId}/stream`)
es.onmessage = (e) => logs.push(JSON.parse(e.data))
es.addEventListener('done', () => es.close())

// 컴포넌트 언마운트 시 해제
onUnmounted(() => es.close())
```

---

## 6. 구현 순서 (TDD)

| 순서 | 작업 | 테스트 대상 |
|------|------|------------|
| 1 | `AgentLog` + `LogType` 도메인 객체 | `AgentLogTest` |
| 2 | `InMemoryAgentLogStore` | `InMemoryAgentLogStoreTest` |
| 3 | `ClaudeAgentExecutor` stream-json 파싱 | `ClaudeAgentExecutorTest` (파싱 검증) |
| 4 | `AgentWorkerService` 상태 전환 시점 | `AgentWorkerServiceTest` |
| 5 | `IssueStatus.REJECTED` + `IssueReviewService` | `IssueReviewServiceTest` |
| 6 | `AgentJobStreamController` SSE | 수동 테스트 (SSE는 통합) |
| 7 | `IssueReviewController` | `IssueReviewControllerTest` |
| 8 | `IssueDetail.vue` SSE 패널 + 리뷰 UI | 수동 검증 |

---

## 7. DB 마이그레이션

`AgentJobStatus`에 PLANNING, CODING, VERIFYING 추가 — `@Enumerated(EnumType.STRING)` 사용 중이므로 별도 마이그레이션 불필요.

`IssueStatus.REJECTED` 추가 — 동일하게 마이그레이션 불필요.

`AgentLog`는 In-Memory 저장 (Phase 2). Phase 3에서 영속화 검토.

---

## 8. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| SSE 연결 수 | 동시 연결 제한 없음 (Virtual Thread 기반) |
| 로그 보존 기간 | 완료 후 1시간 TTL (In-Memory) |
| 재연결 | EventSource 자동 재연결 활용 (`retry:` 필드로 3000ms 설정) |
| 피드백 최대 반복 | 3회 (초과 시 FAILED 전환) |
