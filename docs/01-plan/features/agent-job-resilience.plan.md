# [Plan] Agent Job Resilience

## Executive Summary

| 관점 | 내용 |
|------|------|
| **Problem** | Kafka Consumer 스레드가 장시간(~3분) `handle()` 을 블로킹하는 동안 `session.timeout.ms`가 초과되어 그룹 이탈, 오프셋 커밋 실패 후 동일 메시지 재전달 시 브랜치 중복 생성 오류로 이슈 처리가 영구 실패함 |
| **Solution** | Kafka Consumer 타임아웃 설정 조정으로 세션 타임아웃을 방지하고, `AgentWorkerService` 및 `GitBranchService`에 멱등성 가드를 추가하여 재전달 메시지가 와도 안전하게 처리 |
| **Function UX Effect** | 이슈 생성 후 Claude 에이전트가 PR을 정상 생성하며, 일시적인 Kafka 장애 후에도 작업이 중복 실패 없이 복구됨 |
| **Core Value** | 장시간 실행 작업을 포함하는 Kafka 컨슈머에서 At-Least-Once 재전달을 안전하게 처리하는 내결함성 확보 |

---

## 1. 문제 분석

### 1.1 로그에서 확인된 이벤트 시퀀스

```
23:17:14  Claude 실행 완료 (output: 944)
23:18:11  Kafka session timed out (heartbeat 미수신)
          HikariPool Thread starvation detected (delta=1m4s)
23:18:11  PR push 시작 → 23:18:13 gh error → FAILED
23:18:13  OffsetCommit 실패 (consumer not in active group)
23:18:16  Consumer 그룹 재가입 (generation 17)
          committed offset=2 기준 → issue #3 메시지 재전달
23:18:20  같은 이슈 재처리 시작
23:18:21  git failed: branch 'feat/issue-3-linux' already exists → FAILED
```

### 1.2 근본 원인

| # | 원인 | 증거 |
|---|------|------|
| R-1 | **Kafka `session.timeout.ms` 미설정** (기본 45s): Claude ~2분 + git/gh 작업 중 하트비트 미수신으로 세션 만료 | `session timed out without receiving a heartbeat response` |
| R-2 | **`max.poll.interval.ms` 미설정** (기본 5분): 실제 작업 시간이 close to 5분에 도달할 경우 추가 위험 | 10분 timeout 설정과의 불일치 |
| R-3 | **HikariCP 스레드 starvation**: Virtual Thread 환경에서 블로킹 I/O가 플랫폼 스레드를 고갈시킬 가능성 | `HikariPool-1 Thread starvation (delta=1m4s)` |
| R-4 | **멱등성 부재**: 메시지 재전달 시 동일 branchName 재생성을 방어하는 로직 없음 | `fatal: a branch named 'feat/issue-3-linux' already exists` |
| R-5 | **gh error 원인 불명**: 첫 번째 실행에서 push 성공 후 `gh pr create` 실패 | `gh error` (구체적 메시지 없음) |

---

## 2. 해결 방향

### 2.1 우선순위

| 우선순위 | 항목 | 효과 |
|---------|------|------|
| P0 | Kafka 타임아웃 설정 조정 | 세션 만료 자체를 방지 |
| P0 | `AgentWorkerService` 멱등성 가드 | 재전달 메시지 중복 처리 방지 |
| P1 | `GitBranchService` 브랜치 존재 시 처리 | 브랜치 중복 생성 오류 방지 |
| P1 | `gh pr create` 에러 메시지 구체화 | 원인 파악 및 이미 PR 존재 시 처리 |
| P2 | HikariCP 스레드 starvation 완화 | Virtual Thread 환경 안정화 |

### 2.2 해결 방안 상세

#### Fix-1: Kafka Consumer 타임아웃 설정 (application.properties)

```properties
# Claude 10분 timeout + git/gh 작업 여유분 → 15분
spring.kafka.consumer.properties.max.poll.interval.ms=900000
# session timeout을 충분히 늘림 (기본 45s → 120s)
spring.kafka.consumer.properties.session.timeout.ms=120000
# heartbeat interval = session.timeout.ms / 3
spring.kafka.consumer.properties.heartbeat.interval.ms=40000
```

#### Fix-2: `AgentWorkerService` 멱등성 가드

`handle()` 진입 시 동일 `issueId`의 기존 `AgentJob` 확인:
- `SUCCEEDED` 상태 job 존재 → skip (PR URL 로그)
- `RUNNING` 상태 job 존재 → skip (중복 처리 방지)
- `FAILED` 상태 job 존재 → 재시도 허용 (실패한 작업은 재처리)
- `PENDING` 상태 job 없음 → 정상 신규 처리

```java
// AgentJobRepository에 findByIssueId 이미 존재
agentJobRepository.findByIssueId(event.issueId()).ifPresent(existing -> {
    if (existing.getStatus() == SUCCEEDED || existing.getStatus() == RUNNING) {
        log.warn("[AgentWorker] 이슈 #{} 이미 처리됨 ({}), skip", ...);
        return; // or throw custom skip exception
    }
});
```

#### Fix-3: `GitBranchService` 브랜치 존재 처리

브랜치 생성 전 존재 여부 확인:
- 존재하면 → `git checkout {branchName}` (신규 생성 없이 체크아웃)
- 없으면 → 기존 로직 (checkout base, pull, checkout -b)

```java
// git branch --list {branchName} 로 확인
// 출력이 있으면 이미 존재 → checkout only
```

#### Fix-4: `gh pr create` 에러 처리 개선

`ProcessRunner`에서 에러 메시지가 `"gh failed: {output}"` 형태로 나오지만, `gh error`만 표시됨. 원인:
- `output.trim()` 이 비어있거나 gh CLI가 stderr에 출력 (`redirectErrorStream(true)` 이미 적용)
- 이미 PR이 존재하는 경우 gh CLI가 특정 에러코드/메시지를 반환

대응:
- `PullRequestService.createDraftPr()` 에서 PR 이미 존재 시 (`pr already exists` 포함) URL 추출하여 반환

#### Fix-5: HikariCP 스레드 starvation (선택적)

Virtual Thread와 JDBC 드라이버 호환성:
```properties
spring.datasource.hikari.maximum-pool-size=5
spring.threads.virtual.enabled=true  # 이미 활성화된 경우 확인
```

---

## 3. 구현 범위

### 변경 파일 목록

| 파일 | 변경 유형 | 내용 |
|------|----------|------|
| `src/main/resources/application.properties` | 수정 | Kafka 타임아웃 3개 항목 추가 |
| `agent/application/service/AgentWorkerService.java` | 수정 | 멱등성 가드 (issueId 중복 체크) |
| `agent/application/service/GitBranchService.java` | 수정 | 브랜치 존재 시 checkout only |
| `agent/application/port/AgentJobRepository.java` | 확인 | `findByIssueId` 이미 존재 확인 |
| `agent/application/service/PullRequestService.java` | 수정 | gh error 메시지 개선 + PR 존재 시 처리 |

### 변경하지 않는 파일

- `IssueCreatedEventConsumer` — Kafka 리스너 자체 구조 변경 없음
- `ClaudeAgentExecutor` — 실행 로직 변경 없음
- 도메인 모델 — 멱등성은 application service 계층에서 처리

---

## 4. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| 안정성 | Kafka 재전달 시 중복 처리 없이 안전하게 skip |
| 관찰성 | skip/retry 상황을 명확한 로그 메시지로 구분 |
| 무중단 | 설정 변경은 재시작만 필요, 코드 변경과 동시 배포 가능 |

---

## 5. 구현 순서

1. `application.properties` Kafka 타임아웃 설정 추가 (P0, 3줄)
2. `AgentJobRepository.findByIssueId` 반환 타입 확인
3. `AgentWorkerService.handle()` 멱등성 가드 추가 (P0)
4. `GitBranchService.createBranch()` 브랜치 존재 확인 로직 (P1)
5. `PullRequestService.createDraftPr()` 에러 처리 개선 (P1)
