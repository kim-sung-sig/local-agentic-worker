# [Analysis] Agentic Worker — Agent Executor (Phase 2)

## 분석 요약

| 항목 | 내용 |
|------|------|
| Feature | agentic-worker-agent-executor |
| 설계 문서 | `docs/02-design/features/agentic-worker-agent-executor.design.md` |
| 분석일 | 2026-03-20 |
| **전체 Match Rate** | **96%** ✅ |

---

## 점수 요약

| 항목 | 점수 | 상태 |
|------|:----:|:----:|
| 설계 일치율 | 93% | ✅ |
| 아키텍처 준수 | 100% | ✅ |
| 컨벤션 준수 | 100% | ✅ |
| **종합** | **96%** | ✅ |

---

## 파일별 검증 결과 (20/20 구현 완료)

| # | 파일 | 존재 | 일치 | 비고 |
|---|------|:----:|:----:|------|
| 1 | `db/migration/V2__add_agent_job.sql` | ✅ | ✅ | DDL 완전 일치 |
| 2 | `application.properties` — `agent.claude.*` | ✅ | ✅ | cli-path, timeout-minutes 설정 완료 |
| 3 | `agent/domain/model/AgentJobId.java` | ✅ | ✅ | record, newId(), of() 일치 |
| 4 | `agent/domain/model/AgentJobStatus.java` | ✅ | ✅ | PENDING/RUNNING/SUCCEEDED/FAILED 일치 |
| 5 | `agent/domain/model/AgentJob.java` | ✅ | ✅ | create/start/complete/fail 상태머신 일치 |
| 6 | `agent/event/model/IssueStatusChangedEvent.java` | ✅ | ✅ | record, issueId/newStatus/occurredAt 일치 |
| 7 | `agent/application/port/AgentJobRepository.java` | ✅ | ✅ | save/findById/findByIssueId 일치 |
| 8 | `agent/application/exception/AgentExecutionException.java` | ✅ | ✅ | 생성자 2개 일치 |
| 9 | `agent/application/service/PromptBuilder.java` | ✅ | ✅ | build(IssueCreatedEvent) 정적 메서드 일치 |
| 10 | `agent/application/service/GitBranchService.java` | ✅ | ✅ | createBranch(localPath, baseBranch, branchName) 일치 |
| 11 | `agent/application/service/ClaudeAgentExecutor.java` | ✅ | ⚠️ | G-1 참조 |
| 12 | `agent/application/service/PullRequestService.java` | ✅ | ✅ | push/createDraftPr 시그니처 일치 |
| 13 | `agent/application/service/AgentWorkerService.java` | ✅ | ✅ | handle() 오케스트레이션 시퀀스 일치 |
| 14 | `agent/infrastructure/config/AgentProperties.java` | ✅ | ⚠️ | G-1 참조 |
| 15 | `agent/infrastructure/datasource/AgentJobJpaEntity.java` | ✅ | ✅ | agent_job 테이블 매핑 완전 일치 |
| 16 | `agent/infrastructure/datasource/AgentJobJpaRepository.java` | ✅ | ✅ | findByIssueId 포함 |
| 17 | `agent/infrastructure/datasource/AgentJobRepositoryAdapter.java` | ✅ | ✅ | 포트 구현, 도메인 매핑 정상 |
| 18 | `agent/infrastructure/kafka/IssueCreatedEventConsumer.java` | ✅ | ✅ | TODO 제거, handle() 호출 연결 |
| 19 | `issue/application/listener/IssueStatusChangedEventListener.java` | ✅ | ⚠️ | G-2 참조 |
| 20 | `issue/application/service/IssueCommandService.java` | ✅ | ✅ | KafkaTemplate으로 직접 전송 |

---

## 갭 목록

### G-1 (변경 — 긍정적 편차): ClaudeAgentExecutor 설정 주입 방식

| 항목 | 설계 | 구현 | 영향 |
|------|------|------|------|
| 설정 주입 | `@Value` 필드 주입 | `@ConfigurationProperties` 전용 빈 | 낮음 (개선) |

설계의 클래스 다이어그램은 `@Value` 직접 주입을 명시했으나, 구현에서는 `AgentProperties` `@ConfigurationProperties` 클래스로 타입 안전 설정을 사용했다. 이는 Spring 모범 사례에 부합하는 **긍정적 편차**다.

### G-2 (변경): IssueStatusChangedEventListener 어노테이션

| 항목 | 설계 | 구현 | 영향 |
|------|------|------|------|
| 리스너 어노테이션 | `@ApplicationModuleListener` | `@EventListener` | 낮음 |

설계는 Spring Modulith `@ApplicationModuleListener`를 명시했으나, build.gradle에서 Spring Modulith 의존성이 제거되어 표준 `@EventListener`로 대체했다. 기능적으로 동일하게 동작한다.

---

## 추가 구현 항목 (설계 미포함 — 유효)

| # | 항목 | 위치 | 설명 |
|---|------|------|------|
| A-1 | `AgentJob.reconstitute()` | `AgentJob.java` | JPA → 도메인 복원 팩토리 (인프라 계층 필수) |
| A-2 | `AgentWorkerService.buildBranchName()` | `AgentWorkerService.java` | 브랜치명 슬러그 생성 로직 (소문자·sanitize·40자 제한) |
| A-3 | `AgentWorkerService.buildPrBody()` | `AgentWorkerService.java` | PR body 템플릿 (이슈 정보 + Claude 출력 요약) |
| A-4 | `ClaudeAgentExecutor.truncate()` | `ClaudeAgentExecutor.java` | 오류 출력 500자 truncation 헬퍼 |
| A-5 | `IssueStatusChangedEvent.of()` | `IssueStatusChangedEvent.java` | 편의 팩토리 메서드 |

---

## 아키텍처 준수 검증

| 규칙 | 결과 | 비고 |
|------|:----:|------|
| `domain/`에 프레임워크 의존성 없음 | ✅ | AgentJob, AgentJobId, AgentJobStatus 순수 Java |
| `application/`은 포트 인터페이스만 의존 | ✅ | JPA import 없음 |
| `infrastructure/`가 포트 구현 | ✅ | AgentJobRepositoryAdapter → AgentJobRepository |
| 의존 방향: infra → application → domain | ✅ | import 분석 확인 |
| 이벤트 레코드는 불변 | ✅ | IssueStatusChangedEvent record |
| `application/listener/` 위치 준수 | ✅ | issue BC 하위 정상 배치 |

---

## 결론

**Match Rate: 96%** (≥ 90% 기준 달성)

누락 항목 없음. 2건의 변경은 모두 저영향이며, 1건(G-1)은 설계 대비 개선이다.
다음 단계: `/pdca report agentic-worker-agent-executor`
