# 프로젝트 알림 SSE 구현 계획

> **에이전트 작업자용:** 이 계획은 작업 단위별 개발을 위해 `subagent-driven-development` 또는 `executing-plans`를 사용해야 한다. 모든 단계는 체크박스로 추적한다.

**목표:** 프로젝트별 영속 알림 Inbox와 재연결 가능한 SSE 스트림을 제공하고, Engine의 워크플로 변경·Activity 실행을 `SYSTEM` 발행자로 기록한다.

**아키텍처:** `notification` 컨텍스트가 Inbox와 SSE 재전송 원본을 함께 저장한다. Engine은 Temporal Activity 경계를 통해 알림 명령을 전달하고, 알림 애플리케이션 서비스가 저장 후 커밋 완료 시에만 프로젝트 SSE hub로 브로드캐스트한다.

**기술 스택:** Java 21, Spring Boot, Spring MVC `SseEmitter`, Spring Data JPA, Flyway, JUnit 5, Mockito, Testcontainers PostgreSQL.

---

## 하네스 적용

- 탐색: `code-explorer`가 Issue → Project 해석 경로, Temporal Activity 경계, Flyway·SSE 기존 패턴을 검증한다.
- 구현: `backend-implementer`만 수정한다. 화면은 사용자가 정한 “우선 백엔드” 범위 밖이므로 수정하지 않는다.
- 독립 검토: `backend-reviewer`가 `domain → application → api` 경계, cursor 재전송, 프로젝트 격리, 트랜잭션 이후 브로드캐스트를 읽기 전용으로 확인한다.
- 검증 책임: 구현자는 집중 Gradle 테스트를 실행하고, 오케스트레이터는 리뷰 반영 뒤 같은 테스트와 전체 `gradle test`를 재실행한다.

## 변경 파일 지도

| 경로 | 책임 |
|---|---|
| `src/main/resources/db/migration/V7__add_project_notification.sql` | 알림 테이블·cursor·미읽음 조회 인덱스 |
| `src/main/java/.../notification/domain/model/*` | 순수 알림 모델, 유형, 심각도, 공용 읽음 규칙 |
| `src/main/java/.../notification/application/*` | 저장·조회·읽음·보관·SSE 전달 port와 서비스 |
| `src/main/java/.../notification/infrastructure/*` | JPA 어댑터, 프로젝트별 `SseEmitter` hub |
| `src/main/java/.../notification/api/*` | Inbox·미읽음·읽음·SSE HTTP DTO와 얇은 controller |
| `src/main/java/.../engine/workflow/*` | 알림 Activity 계약과 워크플로 이벤트 호출 |
| `src/main/java/.../engine/infrastructure/activity/EngineActivitiesImpl.java` | 실제 ticket ID 보존과 notification application service 연결 |
| `src/test/java/.../notification/**` | 도메인, application, SSE, persistence 계약 테스트 |
| `src/test/java/.../engine/**` | ticket ID 보존 및 workflow 알림 호출 회귀 테스트 |

### 작업 1: WorkflowRun의 실제 ticket ID를 보존한다

**파일:**
- 수정: `src/main/java/com/example/worker/engine/infrastructure/activity/EngineActivitiesImpl.java`
- 수정: `src/test/java/com/example/worker/engine/infrastructure/activity/EngineActivitiesImplTest.java`

- [ ] **1. 실패하는 회귀 테스트를 작성한다.**

```java
@Test
@DisplayName("워크플로 생성 시 요청 ticketId를 저장한다")
void storesRequestedTicketId() {
    UUID ticketId = UUID.randomUUID();
    activities.assessTicket(new TicketAssessmentRequest(metadata(), ticketId.toString(), "요구사항", 1));

    assertThat(savedWorkflowRun.getTicketId()).isEqualTo(ticketId);
}
```

- [ ] **2. 실패를 확인한다.**

`./gradlew test --tests "*EngineActivitiesImplTest"`를 실행한다. 현재 `workflowRunId`를 UUID로 변환해 저장하므로 실패해야 한다.

- [ ] **3. 최소 구현을 작성한다.**

`ensureWorkflowRunExists`가 `TicketAssessmentRequest.ticketId()`를 `UUID`로 변환해 `WorkflowRun.create(ticketId, workflowRunId)`를 호출하게 바꾼다. `recordAttemptHistory`의 fallback 생성은 제거하고, 존재하지 않는 run이면 명시적으로 실패시킨다.

- [ ] **4. 집중 테스트를 통과시킨다.**

동일 명령이 PASS하고, workflow run의 ticket ID가 요청 값과 같은지 확인한다.

### 작업 2: 알림 도메인·영속 구조를 만든다

**파일:**
- 생성: `src/main/resources/db/migration/V7__add_project_notification.sql`
- 생성: `src/main/java/com/example/worker/notification/domain/model/Notification.java`
- 생성: `src/main/java/com/example/worker/notification/domain/model/NotificationType.java`
- 생성: `src/main/java/com/example/worker/notification/domain/model/NotificationSeverity.java`
- 생성: `src/main/java/com/example/worker/notification/application/port/NotificationRepository.java`
- 생성: `src/main/java/com/example/worker/notification/infrastructure/datasource/NotificationJpaEntity.java`
- 생성: `src/main/java/com/example/worker/notification/infrastructure/datasource/NotificationJpaRepository.java`
- 생성: `src/main/java/com/example/worker/notification/infrastructure/datasource/NotificationRepositoryAdapter.java`
- 생성: `src/test/java/com/example/worker/notification/domain/model/NotificationTest.java`
- 생성: `src/test/java/com/example/worker/notification/infrastructure/datasource/NotificationRepositoryAdapterTest.java`

- [ ] **1. 도메인 테스트를 먼저 작성한다.** `Notification.create(...)`가 `publisher`를 `SYSTEM`으로 고정하고, `markRead`를 반복 호출해도 최초 `readAt`을 유지하며, cursor 이후 항목만 반환할 수 있는 repository 계약을 검증한다.
- [ ] **2. Flyway migration을 추가한다.** `project_notification` 테이블에 `id BIGSERIAL`, `event_id`, `notification_id`, `project_id`, nullable `workflow_run_id`, `type`, `severity`, `publisher`, `title`, `message`, `read_at`, `created_at`을 생성한다. `(project_id, id)`와 `(project_id, read_at, id)` 인덱스를 만든다. API event ID는 내부 `id`를 고정 폭으로 인코딩한 불투명 값으로 생성한다.
- [ ] **3. 순수 모델과 JPA adapter를 구현한다.** 도메인에는 Spring/JPA import를 두지 않고, adapter만 entity 변환과 `id > cursorId` 조회를 담당하게 한다.
- [ ] **4. persistence 테스트를 실행한다.** PostgreSQL Testcontainers에서 같은 프로젝트의 cursor 순서·다른 프로젝트 격리·공용 unread count를 검증한다.

### 작업 3: 알림 application service와 프로젝트 SSE hub를 만든다

**파일:**
- 생성: `src/main/java/com/example/worker/notification/application/dto/CreateNotificationCommand.java`
- 생성: `src/main/java/com/example/worker/notification/application/dto/NotificationPage.java`
- 생성: `src/main/java/com/example/worker/notification/application/service/NotificationCommandService.java`
- 생성: `src/main/java/com/example/worker/notification/application/service/NotificationQueryService.java`
- 생성: `src/main/java/com/example/worker/notification/application/service/NotificationRetentionService.java`
- 생성: `src/main/java/com/example/worker/notification/application/port/NotificationStreamPublisher.java`
- 생성: `src/main/java/com/example/worker/notification/infrastructure/sse/ProjectNotificationSseHub.java`
- 수정: `src/main/java/com/example/worker/WorkerApplication.java`
- 생성: `src/test/java/com/example/worker/notification/application/service/NotificationCommandServiceTest.java`
- 생성: `src/test/java/com/example/worker/notification/infrastructure/sse/ProjectNotificationSseHubTest.java`

- [ ] **1. 실패하는 service 테스트를 작성한다.** 저장 성공 뒤 publisher가 같은 `projectId`의 `notification.created`만 받는지, repository 예외면 publisher가 호출되지 않는지, `markRead`가 한 번의 `notification.read`만 발행하는지 검증한다.
- [ ] **2. 최소 application service를 구현한다.** command service는 저장 후 `TransactionSynchronization`의 after-commit callback에서 publisher를 호출한다. query service는 cursor page, unread count, 단건·최대 100건 bulk read를 제공한다.
- [ ] **3. SSE hub를 구현한다.** `ConcurrentHashMap<UUID, Set<SseEmitter>>`로 프로젝트별 다중 구독을 관리한다. 30분 timeout, `retry: 3000`, completion/timeout/error 제거, `connected`와 15~30초 heartbeat를 제공한다. 전송 실패 emitter만 제거한다.
- [ ] **4. 보관 작업을 구현한다.** `@EnableScheduling`을 애플리케이션에 추가하고, 매일 `createdAt < now - 30 days`를 삭제하는 service를 `@Scheduled`로 실행한다.
- [ ] **5. 집중 테스트를 실행한다.** command service와 hub 테스트가 PASS하며, 커밋 전 브로드캐스트가 없는지 확인한다.

### 작업 4: 프로젝트 Inbox와 SSE API를 제공한다

**파일:**
- 생성: `src/main/java/com/example/worker/notification/api/controller/ProjectNotificationController.java`
- 생성: `src/main/java/com/example/worker/notification/api/request/MarkNotificationsReadRequest.java`
- 생성: `src/main/java/com/example/worker/notification/api/response/NotificationResponse.java`
- 생성: `src/main/java/com/example/worker/notification/api/response/NotificationPageResponse.java`
- 생성: `src/main/java/com/example/worker/notification/api/response/UnreadNotificationCountResponse.java`
- 생성: `src/test/java/com/example/worker/notification/api/controller/ProjectNotificationControllerTest.java`

- [ ] **1. API 계약 테스트를 작성한다.** 목록은 cursor를 받고 최신순 페이지와 next cursor를 반환한다. unread count는 프로젝트 범위다. 단건·일괄 읽음은 멱등이며 bulk body는 100건을 초과하면 `400`이다.
- [ ] **2. SSE 재연결 계약 테스트를 작성한다.** `Last-Event-ID`가 있으면 같은 프로젝트의 이후 알림만 `eventId` 순서로 보낸다. 다른 프로젝트 알림은 포함하지 않는다. cursor가 30일 이전이거나 존재하지 않으면 `reset`을 전송한다.
- [ ] **3. 얇은 controller와 response record를 구현한다.** controller는 `projectId` path variable·검증된 request·`Last-Event-ID`만 application service에 전달한다. payload는 domain/JPA entity를 직접 노출하지 않는다.
- [ ] **4. controller 테스트를 실행한다.** MockMvc async SSE와 JSON contract가 PASS하는지 확인한다.

### 작업 5: Engine 이벤트를 알림 command로 연결한다

**파일:**
- 수정: `src/main/java/com/example/worker/engine/workflow/EngineActivities.java`
- 수정: `src/main/java/com/example/worker/engine/application/contract/v1/NotificationRequest.java`
- 수정: `src/main/java/com/example/worker/engine/infrastructure/activity/EngineActivitiesImpl.java`
- 수정: `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflowImpl.java`
- 수정: `src/test/java/com/example/worker/engine/workflow/AgentWorkerWorkflowTest.java`
- 수정: `src/test/java/com/example/worker/engine/infrastructure/activity/EngineActivitiesImplTest.java`

- [ ] **1. workflow 테스트를 먼저 확장한다.** 생성, 단계/상태 변경, Attempt 기록, 결정, 각 Activity 시작·완료, Activity 실패가 `sendNotification` Activity 호출로 남는지 검증한다. QA 자동 재시도와 최종 실패도 포함한다.
- [ ] **2. 기존 `sendNotification` Activity를 확장한다.** `NotificationRequest`에 type, severity, title, message를 추가한다. Activity 구현은 workflow run의 실제 ticket ID를 통해 `IssueRepository.findById`로 project ID를 해석한 뒤 `NotificationCommandService`만 호출한다.
- [ ] **3. workflow에서 최소 호출을 추가한다.** 각 handler의 상태 전이와 Activity 호출 전·후에 알림을 보낸다. `run` 최상단에서 Activity 예외를 잡아 `ACTIVITY_FAILED` ERROR 알림을 보낸 뒤 원래 예외를 다시 던진다. 기존 Temporal signal handler는 Spring bean을 직접 참조하지 않는다.
- [ ] **4. Engine 집중 테스트를 통과시킨다.** Temporal test environment에서 알림 Activity 호출 순서와 QA 실패/재시도 경로를 확인한다.

### 작업 6: 전체 검증과 문서 동기화를 수행한다

**파일:**
- 수정: `docs/contracts/operator-workflow-console-realtime-notification-api-request.md`
- 수정: `docs/superpowers/specs/2026-07-18-project-notification-sse-design.md`

- [ ] **1. 계약 문서를 구현 API에 맞춘다.** 전역 stream 경로를 프로젝트 stream 경로로 바꾸고, 사용자별 소유권·외부 채널은 현재 범위 밖임을 유지한다.
- [ ] **2. 백엔드 전체 테스트를 실행한다.** `./gradlew test`를 실행한다. 기존 실패가 있으면 새 실패와 분리해 기록한다.
- [ ] **3. migration 검증을 실행한다.** PostgreSQL Testcontainers 대상 migration과 repository integration test를 실행한다.
- [ ] **4. 변경 범위를 검토하고 커밋한다.** `git diff --check` 뒤 알림 기능 파일만 하나의 기능 커밋으로 만든다.

## 자체 검토

- 설계의 프로젝트 격리, `SYSTEM` 발행자, Inbox·공용 읽음, 30일 보관, `Last-Event-ID` 재전송·reset, 모든 workflow/Activity 이벤트를 작업 1~5에 연결했다.
- 계정·프로젝트 팀 권한, Slack/SMTP, 화면 변경은 명시적으로 제외했다.
- 실제 ticket ID가 저장되지 않아 project ID를 해석할 수 없는 현재 결함을 작업 1에서 선행 수정한다.
- 새 의존성은 추가하지 않으며, 기존 Spring MVC·JPA·Flyway·Testcontainers만 사용한다.
