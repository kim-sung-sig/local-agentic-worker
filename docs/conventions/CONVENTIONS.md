# CONVENTIONS.md

Project-wide development principles and conventions for the Chat Platform.

---

## Core Principles

- **SOLID**: single responsibility, open/closed, Liskov substitution, interface segregation, dependency inversion.
- **Interfaces first**: define explicit port interfaces before implementations; allow partial interfaces for incremental capabilities.
- **No magic constants**: centralize magic numbers/strings in constants, enums, or domain types — never inline them.
- **Model-based design (DDD)**: model domain concepts explicitly; keep domain logic in the domain layer, not in services or controllers.
- **TDD**: write tests first for domain logic and critical behavior; add regression tests for every bug fix.

---

## Design & Structure

- Favor small, composable units with clear single responsibilities.
- Keep I/O and side effects at system boundaries (controllers, Kafka consumers, Redis adapters); keep core domain logic pure.
- Use constructor injection for all external dependencies.
- **Early return** over nested if-else — keep cyclomatic complexity low.
- No business logic in `api/` controllers or persistence adapters.

---

## DDD Layering

Each bounded context follows this strict layering:

```
<context>/
├── domain/
│   ├── model/        ← Entity, Value Object, Enum (pure Java, 프레임워크 의존 금지)
│   └── service/      ← Domain Service (비즈니스 규칙, 상태 없는 순수 로직)
│
├── application/
│   ├── service/      ← CommandService / QueryService (Use Case, Facade)
│   ├── dto/          ← 레이어 간 이동 DTO (Application 경계 내부용)
│   └── listener/     ← 이벤트 핸들러 (@EventListener, 도메인 서비스 오케스트레이션)
│
├── event/
│   └── model/        ← 이벤트 페이로드 값 객체 (불변 record, 도메인 이벤트 데이터)
│
├── infrastructure/
│   ├── kafka/        ← Kafka consumer/producer 어댑터
│   ├── redis/        ← Cache + Pub/Sub 어댑터
│   └── datasource/   ← JPA Repository 어댑터 (domain port 구현)
│
└── api/
    ├── controller/   ← REST 엔드포인트 (얇음, application layer 위임만)
    ├── request/      ← HTTP 요청 DTO (record, 불변)
    └── response/     ← HTTP 응답 DTO (record, 불변, factory method)
```

**레이어 의존 방향**:
```
api/ → application/ → domain/
infrastructure/ → application/ → domain/
application/listener/ → event/model/ (이벤트 페이로드 참조)
```

**레이어 규칙**:
- `domain/` : 프레임워크 의존 **완전 금지** (순수 Java 객체만)
- `application/` : Spring `@Service` 허용, JPA 직접 의존 금지
- `infrastructure/` : 외부 기술 어댑터, domain port 인터페이스 구현
- `api/` : 비즈니스 로직 **완전 금지**, application layer 위임만

**event/listener 배치 원칙**:
- `event/model/` : 이벤트 페이로드 VO (값 객체, 데이터 전달 역할)
- `application/listener/` : 이벤트 핸들러 (HTTP 요청 대신 이벤트를 input으로 받는 Application Service와 동일한 책임)
- `domain/` 계층에 `@EventListener` 금지 (Spring 의존 발생)

---

## Presentation Layer (api/)

**핵심 원칙**: `api/response/`와 `api/request/`의 모든 클래스는 Java `record` 사용

### 객체 생성 책임

응답/요청 객체는 **스스로 생성에 대한 책임**을 가집니다.
서비스 레이어에서 Response 필드를 직접 채우는 것은 금지합니다.

```java
// ✅ 올바른 패턴 — record + factory method
public record ChannelResponse(Long id, String name, LocalDateTime createdAt) {
    public static ChannelResponse from(Channel channel) {
        return new ChannelResponse(channel.getId(), channel.getName(), channel.getCreatedAt());
    }
}

// 서비스에서 사용
return ChannelResponse.from(channel);  // ✅ factory method 위임

// ❌ 금지 패턴 — 서비스에서 Response 필드 직접 조작
ChannelResponse response = new ChannelResponse();
response.setId(channel.getId());  // record는 setter 없음 (컴파일 에러)
```

**규칙 목록**:
1. `record` 사용 — 모든 필드 자동 불변 (`final` 보장)
2. `public static XxxResponse from(DomainObject)` factory method 필수 작성
3. 서비스는 `XxxResponse.from(domainObj)` 호출만 허용
4. `api/request/` record는 `@Valid`, `@NotNull` 등 Bean Validation 어노테이션 허용
5. 도메인 타입은 Response로 노출 금지 — `api/` 경계에서 반드시 변환

---

## CQRS

- Write operations → `XxxCommandService`
- Read operations → `XxxQueryService`
- Read queries use **cursor-based pagination** (no offset).
- Write datasource: `source`; read datasource: `replica`.

---

## Testing Conventions

- **Structure**: JUnit 5 `@Nested` per method under test, with `HappyPath`, `Boundary`, `Failure` nested groups as needed.
- **Mocking**: use `@Mock` + `@InjectMocks` (Mockito); mock repositories and external adapters; never mock the domain itself.
- **Display names**: `@DisplayName` in **Korean** for all test classes and methods.
- **Pattern**: Given / When / Then in method bodies and inline comments.
- **Fixtures**: use builders or fixtures to avoid repetitive setup; tests must document intent and edge cases.
- Prefer domain/service unit tests over API tests; add API tests only for contract verification.
- Use TestContainers for integration tests requiring real PostgreSQL or Kafka.

---

## Code Style

- All source files and documents: **UTF-8** encoding.
- Language: Java 21 + Kotlin 1.9; virtual threads are enabled — avoid `ThreadLocal` patterns.
- Lombok (`@Getter`, `@Builder`, etc.) for boilerplate reduction; avoid `@Data` on JPA entities.
- SDD (`docs/specs/SDD_<slug>.md`) is the authoritative spec for every feature; keep domain language consistent with the SDD.

---

## Process

- If a change violates any principle above, call it out and propose an alternative.
- Ask before proceeding when requirements are ambiguous or could change data model boundaries.
- When implementing from an SDD, follow the skill chain: `/sdd:requirements` → `/sdd:read` → `/sdd:skeleton` → `/sdd:tests` → `/sdd:review`.
