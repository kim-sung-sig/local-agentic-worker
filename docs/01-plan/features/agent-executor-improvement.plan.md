# [Plan] AgentExecutor 개선 & 프로젝트 고도화 로드맵

## Executive Summary

| 관점 | 내용 |
|------|------|
| **Problem** | `ClaudeAgentExecutor`가 `--dangerously-skip-permissions` + 블로킹 subprocess 방식으로 동작하여 가시성 없음, 진행상황 추적 불가, 보안 위험이라는 3가지 핵심 문제를 가짐 |
| **Solution** | 단계별 3-Track 개선: ① CLI streaming 전환으로 즉각적 가시성 확보, ② Anthropic Java SDK 직접 호출로 보안 플래그 제거, ③ 승인/반려 피드백 루프 + 자가검증 UI 구현 |
| **Function UX Effect** | 실시간 SSE 스트리밍으로 에이전트 진행상황 확인, 보안 권한 제어 정밀화, 반려 시 피드백 기반 재시도 자동화 |
| **Core Value** | 사람이 승인·감독하는 Human-in-the-Loop AI 개발 파이프라인으로 신뢰성과 통제권 동시 확보 |

---

## 1. 현황 분석

### 1.1 현재 ClaudeAgentExecutor 동작 방식

```
Kafka Event → AgentWorkerService.handle() [BLOCKING]
  → ProcessRunner.run("claude --print --dangerously-skip-permissions -p {prompt}")
  → 최대 10분 블로킹 대기
  → 전체 출력 String 반환
```

### 1.2 문제점 상세

| # | 문제 | 구체적 증상 | 위험도 |
|---|------|------------|--------|
| P1 | **가시성 없음** | Claude가 무엇을 하는지 실시간 확인 불가. PR 생성 후에야 결과 확인 | 중 |
| P2 | **진행상황 추적 불가** | `AgentJobStatus`가 RUNNING 고정. 어느 단계인지(계획중/코딩중/테스트중) 모름. 중간 취소 불가 | 중 |
| P3 | **보안 위험** | `--dangerously-skip-permissions` 플래그가 파일시스템 전체 쓰기, 임의 bash 실행을 무제한 허용. 잘못된 프롬프트 주입 시 서버 파괴 가능 | **고** |

---

## 2. 개선 방향 (3-Track)

### Track A: 즉각 개선 — CLI Streaming + 진행상황 이벤트 (2-3일)

**목표**: 기존 subprocess 방식 유지하되 streaming으로 실시간 출력 확보

#### A-1. `claude --output-format stream-json` 전환

```bash
# 현재 (블로킹, 전체 출력 후 반환)
claude --print --dangerously-skip-permissions -p "{prompt}"

# 개선 (stream-json: 이벤트별 JSON 라인 스트리밍)
claude --output-format stream-json --dangerously-skip-permissions -p "{prompt}"
```

stream-json 이벤트 형태:
```json
{"type":"assistant","message":{"content":[{"type":"text","text":"분석 중..."}]}}
{"type":"tool_use","name":"Write","input":{"file_path":"src/..."}}
{"type":"result","result":"완료"}
```

#### A-2. `AgentJobStatus` 세분화

```java
// 현재
PENDING → RUNNING → SUCCEEDED / FAILED

// 개선
PENDING → PLANNING → CODING → TESTING → VERIFYING → SUCCEEDED / FAILED
```

#### A-3. SSE 엔드포인트 추가

```
GET /api/agent-jobs/{jobId}/stream  → Server-Sent Events
```
- `ProcessRunner` stdout을 라인별로 파싱 → SSE emit
- 프론트엔드에서 EventSource로 실시간 구독

---

### Track B: 핵심 개선 — `--dangerously-skip-permissions` 제거 (1주)

**3가지 대안 비교:**

| 방안 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **B-1. `.claude/settings.json` 허용 목록** | 프로젝트별 허용 tool 패턴 사전 정의 | 간단, CC 기능 그대로 활용 | 허용 범위 관리 필요 |
| **B-2. `--permission-prompt-tool` MCP** | 커스텀 MCP 서버가 권한 승인/거부 판단 | 동적 제어 가능 | MCP 서버 구현 필요 |
| **B-3. Anthropic Java SDK 직접 호출** | subprocess 없이 API 직접 호출, tool 구현을 Java로 | 완전한 제어, 보안 강화 | git/file tool 직접 구현 필요 |

**권장: B-1 (단기) → B-3 (장기)**

#### B-1 즉각 적용 — `.claude/settings.json` 허용 목록

```json
{
  "permissions": {
    "allow": [
      "Bash(git *)",
      "Bash(./gradlew *)",
      "Bash(npm *)",
      "Write(src/**)",
      "Edit(src/**)",
      "Read(**)"
    ],
    "deny": [
      "Bash(rm -rf *)",
      "Bash(curl *)",
      "Bash(wget *)"
    ]
  }
}
```

**실행 명령 변경**:
```bash
# --dangerously-skip-permissions 제거
claude --print -p "{prompt}"
# → settings.json의 allow/deny 목록으로 제어
```

---

### Track C: 기능 고도화 — 승인/반려 피드백 루프 (2주)

**현재 흐름의 빠진 부분:**
```
Draft PR 생성 → ??? (사용자가 수동으로 GitHub에서 확인)
```

**목표 흐름:**
```
Draft PR 생성
  → [웹UI] 개발 내역 표시 (diff, Claude 작업 요약)
  → 승인 버튼 → PR ready for review 전환 + Issue DONE
  → 반려 버튼 + 피드백 입력
      → 피드백을 새 프롬프트에 포함
      → 동일 브랜치에서 Claude 재실행
      → Loop: 자가검증 → 재PR
```

#### C-1. Issue 상태 확장

```java
// 추가 상태
IN_REVIEW,      // Draft PR 존재, 사용자 검토 대기
APPROVED,       // 승인 → PR merge 요청
REJECTED,       // 반려 → 피드백 포함 재개발 트리거
DONE            // 최종 완료
```

#### C-2. 반려 피드백 API

```
POST /api/issues/{issueId}/review
Body: { "approved": false, "feedback": "테스트 코드 누락, 엣지케이스 처리 필요" }
```
→ `IssueRejectedEvent` 발행 → `AgentWorkerService`가 피드백 포함 재실행

#### C-3. `PromptBuilder` 피드백 반영

```java
// 재시도 시 이전 피드백 포함
if (retryCount > 0) {
    prompt += "\n\n## 이전 검토 피드백\n" + feedbackHistory;
    prompt += "\n위 피드백을 반드시 반영하여 개선하라.";
}
```

---

## 3. Anthropic Java SDK 직접 호출 (장기 목표)

**현재 subprocess 방식의 근본 한계 해소:**

```java
// 현재: 블랙박스 subprocess
ProcessRunner.run("claude", "--print", "--dangerously-skip-permissions", "-p", prompt)

// 목표: Anthropic Java SDK
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.MessageCreateParams;

AnthropicClient client = AnthropicClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .build();

// 스트리밍 + 도구 사용 정의
MessageCreateParams params = MessageCreateParams.builder()
    .model("claude-opus-4-6")
    .maxTokens(8096)
    .addTool(Tool.builder().name("write_file").description("파일 작성").build())
    .addTool(Tool.builder().name("run_git").description("git 명령 실행").build())
    .addUserMessage(prompt)
    .build();

// 스트리밍으로 실시간 진행상황 수신
client.messages().stream(params)
    .on(MessageStreamEvent.class, event -> sseEmitter.send(event));
```

**장점:**
- `--dangerously-skip-permissions` 완전 제거
- 허용 도구(write_file, run_git 등)를 Java 코드로 직접 구현 → 세밀한 제어
- 스트리밍 기본 지원
- API 레벨 에러 처리 (retry, timeout 등)

---

## 4. 구현 로드맵

### Phase 1 (이번 스프린트, 3-5일)
| 작업 | 파일 | 우선순위 |
|------|------|---------|
| `.claude/settings.json` 허용 목록 설정 | `.claude/settings.json` | **P0** |
| `--dangerously-skip-permissions` 제거 | `ClaudeAgentExecutor.java` | **P0** |
| `AgentJobStatus` 단계 세분화 | `AgentJobStatus.java`, `AgentJob.java` | P1 |
| CLI stream-json 전환 + 진행상황 저장 | `ClaudeAgentExecutor.java`, `ProcessRunner.java` | P1 |

### Phase 2 (다음 스프린트, 1주)
| 작업 | 파일 | 우선순위 |
|------|------|---------|
| SSE 스트리밍 엔드포인트 | `AgentJobStreamController.java` | P1 |
| 승인/반려 API | `IssueReviewController.java` | P1 |
| 피드백 재시도 루프 | `AgentWorkerService.java`, `PromptBuilder.java` | P1 |
| 웹UI 실시간 진행상황 화면 | `frontend/` | P2 |

### Phase 3 (중기, 2-3주)
| 작업 | 파일 | 우선순위 |
|------|------|---------|
| Anthropic Java SDK 도입 | `pom.xml` / `build.gradle` | P2 |
| SDK 기반 AgentExecutor 교체 | `ClaudeAgentExecutor.java` (재설계) | P2 |
| Java 구현 도구 (WriteFile, RunGit, RunTest) | `agent/infrastructure/tools/` | P2 |

---

## 5. 보안 체크리스트

- [ ] `--dangerously-skip-permissions` 제거 확인
- [ ] `.claude/settings.json` deny 목록: `curl`, `wget`, `rm -rf`, `chmod`, `sudo` 차단
- [ ] `workDir` 경로 검증 (path traversal 방지)
- [ ] 프롬프트 인젝션 방어: issue 제목/설명의 특수문자 이스케이프
- [ ] API Key를 환경변수로만 관리, 코드 하드코딩 금지
- [ ] AgentJob 실행 결과 로그에 민감정보 마스킹

---

## 6. 관련 문서

- [agent-job-resilience.plan.md](./agent-job-resilience.plan.md) — Kafka 멱등성 & 타임아웃 개선
- [CONVENTIONS.md](../../conventions/CONVENTIONS.md) — DDD 레이어 규칙
- Anthropic Java SDK: `com.anthropic:anthropic-java` (Maven Central)
