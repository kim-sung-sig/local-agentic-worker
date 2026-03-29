---
name: tdd-cycle
description: >
  Drive progressive TDD improvement through Red-Green-Refactor cycles.
  Use this skill:
    * When a test is failing and you need a structured fix approach
    * When reviewing test coverage gaps after implementation
    * When iterating test quality (edge cases, boundary, failure paths)
    * When deciding whether to add integration vs unit tests
    * After /skeleton-to-tests to validate and improve test quality
---

# TDD Cycle Skill

Guides the Red → Green → Refactor cycle and tracks test improvement over iterations.

---

## The Cycle

```
RED   → Write a failing test that describes the requirement
GREEN → Write minimal code to make the test pass (no gold-plating)
REFACTOR → Clean code without breaking tests
```

Repeat per use case unit. Never skip RED — if there's no failing test first,
it's not TDD.

---

## Phase: RED (Test First)

Before writing any implementation, confirm:

1. Test class exists under `src/test/java/com/example/chat/<context>`
2. Test method describes ONE behavior (`@DisplayName` 한국어, 구체적으로)
3. Test compiles but **fails** (expected behavior not yet implemented)

```java
@Test
@DisplayName("예약 메시지 생성 - 과거 시간이면 예외 발생")
void createScheduledMessage_pastTime_throwsException() {
    // Given
    var request = new CreateScheduledMessageRequest(...pastTime...);
    // When / Then
    assertThatThrownBy(() -> sut.create(request))
        .isInstanceOf(ChatException.class)
        .hasMessageContaining("과거 시간");
}
```

---

## Phase: GREEN (Minimal Implementation)

Rules:
- Write only what makes the failing test pass
- Do NOT add features not tested yet
- Hardcoding is acceptable temporarily (fix in REFACTOR)
- Run: `./gradlew :apps:chat:chat-server:test --tests "FullyQualifiedTestClass" --no-daemon`

---

## Phase: REFACTOR

Checklist before marking test cycle complete:

- [ ] No duplication between tests (extract fixtures/builders)
- [ ] Each test has ONE assertion reason (SRP for tests)
- [ ] `@Nested` groups are used: `HappyPath`, `Boundary`, `Failure`
- [ ] No magic strings/numbers in tests (use constants or test fixtures)
- [ ] Domain logic not re-tested through API layer (unit test for domain, API test only for contract)

---

## Test Matrix Template

For each method under test, fill:

| Scenario | Type | Input | Expected | Covered? |
|----------|------|-------|----------|----------|
| 정상 생성 | HappyPath | valid request | entity saved | [ ] |
| 과거 시간 | Failure | pastTime | ChatException | [ ] |
| 경계값 (정각) | Boundary | now() | depends on policy | [ ] |

---

## Coverage Tiers

| Tier | Target | When |
|------|--------|------|
| Domain model | 100% happy + failure | Always |
| CommandService | happy + all failure branches | Always |
| QueryService | happy + empty result | Always |
| Controller | contract test only | When API exists |
| Integration | E2E with Testcontainers | Critical flows only |

---

## Test Data Builder Pattern

반복적인 fixture 설정은 Builder로 추출. REFACTOR 단계에서 적용.

```java
// 나쁜 예 — 테스트마다 필드 반복
ScheduledMessage msg = new ScheduledMessage(1L, memberId, channelId,
    "내용", ScheduleType.ONCE, LocalDateTime.now().plusHours(1),
    ScheduleStatus.PENDING);

// 좋은 예 — Builder 추출
public class ScheduledMessageFixture {
    public static ScheduledMessage pending() {
        return ScheduledMessage.builder()
            .memberId(1L)
            .channelId(10L)
            .content("테스트 메시지")
            .scheduleType(ScheduleType.ONCE)
            .scheduledAt(LocalDateTime.now().plusHours(1))
            .status(ScheduleStatus.PENDING)
            .build();
    }

    public static ScheduledMessage withStatus(ScheduleStatus status) {
        return pending().toBuilder().status(status).build();
    }

    public static ScheduledMessage pastTime() {
        return pending().toBuilder()
            .scheduledAt(LocalDateTime.now().minusMinutes(1))
            .build();
    }
}

// 테스트에서 사용
ScheduledMessage msg = ScheduledMessageFixture.pending();
ScheduledMessage expired = ScheduledMessageFixture.pastTime();
```

**Fixture 배치**: `src/test/java/com/example/chat/<context>/fixture/`

---

## Kafka Testing Pattern (Testcontainers)

Kafka 통합 테스트는 Testcontainers로 실제 브로커 사용.

```java
@Testcontainers
@SpringBootTest
@EmbeddedKafka  // 단순 경우: EmbeddedKafka (빠름)
class MessageKafkaListenerTest {

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired MessageRepository messageRepository;

    @Test
    @DisplayName("Kafka 메시지 수신 후 DB 저장 - HappyPath")
    void consumeMessage_savesToDB() throws Exception {
        // Given
        var event = new ChatMessageEvent(1L, "테스트");

        // When
        kafkaTemplate.send("chat.messages", event).get();

        // Then — 비동기 처리 대기
        await().atMost(5, SECONDS)
            .until(() -> messageRepository.count() > 0);
        assertThat(messageRepository.findAll()).hasSize(1);
    }
}

// Testcontainers 방식 (kafka-test dependency 불필요)
@Testcontainers
class KafkaContainerTest {
    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

**규칙**: Producer 단위 테스트 → Mock, Consumer 통합 테스트 → Testcontainers/EmbeddedKafka

---

## Incremental Improvement

After each GREEN cycle, run:

```bash
./gradlew :apps:chat:chat-server:test --no-daemon
```

If tests pass → move to REFACTOR.
If any test fails → stay in GREEN, do not REFACTOR yet.

After REFACTOR → run full compile check:

```bash
./gradlew compileJava compileTestJava --no-daemon
```

---

## Review Criteria (link to $sdd-review)

Before handing off:
- [ ] All Spec Checklist items have at least one test
- [ ] Test names are self-documenting (no "test1", "case2")
- [ ] Boundary conditions tested (null, empty, max/min)
- [ ] Failure paths throw correct `ChatErrorCode`

---

## Skill Connection Flow

```
$skeleton-to-tests  (generates initial test stubs)
  → $tdd-cycle      (this skill — iterative improvement)
    RED: failing test
    GREEN: pass
    REFACTOR: clean
  → $sdd-review     (final review)
  → $done-gate      (gate check)
```