---
name: java-best-practices
description: Opinionated modern Java (21+) coding best practices, style guides, and anti-patterns. Curated by VirtusLab engineers. Covers code style, null safety, error handling, immutability, testing, concurrency, and tooling.
---

# Java Best Practices by VirtusLab

Opinionated, modern Java skill. Targets Java 21+ and explicitly cuts off outdated patterns. When writing or reviewing Java code, follow these guidelines strictly.

## Core Philosophy

- **Composed Method Pattern** is the single most impactful practice in Java. Every method should do one thing, at one level of abstraction, and be short enough to read in one glance. This pattern alone transforms unreadable code into clean code.
- **Effective Java by Joshua Bloch** remains the authoritative reference. Its principles are still current. When in doubt, consult it.
- **Use library code.** Do not reimplement what the standard library or a well-established library already provides. This is one of the most violated and most impactful principles.
- **Composition over inheritance.** Always. Use interfaces with default methods and delegation instead of deep class hierarchies.
- **GRASP principles** (General Responsibility Assignment Software Patterns) guide object-oriented design: Information Expert, Creator, Controller, Low Coupling, High Cohesion, Polymorphism, Pure Fabrication, Indirection, Protected Variations.

## Modern Java (21+) — Use These Features

Use modern Java features aggressively. Do not write pre-Java 17 style code.

### Records for Data Objects

Use `record` for all immutable data carriers. Do NOT use Lombok `@Value`, `@Data`, `@Getter`, or hand-written POJOs for data objects.

```java
// WRONG — legacy style
@Value
public class User {
    String username;
    String email;
}

// WRONG — hand-written boilerplate
public class User {
    private final String username;
    private final String email;
    // constructor, getters, equals, hashCode, toString...
}

// CORRECT
public record User(String username, String email) {}
```

Lombok is acceptable **only** for `@Builder` on non-record classes where the builder pattern is genuinely needed. For everything else, use records or plain Java.

### Sealed Classes for Domain Modeling (ADTs)

Use `sealed` classes and interfaces to model algebraic data types. This gives exhaustiveness checking in `switch` expressions.

```java
public sealed interface PaymentResult
    permits PaymentSuccess, PaymentFailure, PaymentPending {
}

public record PaymentSuccess(String transactionId, Money amount) implements PaymentResult {}
public record PaymentFailure(String reason, ErrorCode code) implements PaymentResult {}
public record PaymentPending(String transactionId) implements PaymentResult {}
```

### Switch Expressions with Pattern Matching

Use switch expressions (not statements) with pattern matching. Prefer exhaustive switches.

```java
// CORRECT
String message = switch (result) {
    case PaymentSuccess success ->
        "Paid %s".formatted(success.amount());
    case PaymentFailure failure ->
        "Failed: %s".formatted(failure.reason());
    case PaymentPending pending ->
        "Pending: %s".formatted(pending.transactionId());
};

// WRONG — old-style switch statement with break
switch (result.getType()) {
    case SUCCESS:
        message = "Paid";
        break;
    // ...
}
```

### Null Handling Inside `switch` (Java 21+)

```java
// CORRECT — null is an explicit case
return switch (status) {
    case null -> "unknown";
    case ACTIVE -> "active";
    case PAUSED -> "paused";
};

// WRONG — null handled outside the switch
if (status == null) return "unknown";
return switch (status) {
    case ACTIVE -> "active";
    case PAUSED -> "paused";
};
```

### Pattern Matching for `instanceof`

```java
// CORRECT
if (obj instanceof String s && !s.isBlank()) {
    process(s);
}

// WRONG
if (obj instanceof String) {
    String s = (String) obj;
    process(s);
}
```

### Text Blocks

Use text blocks for multi-line strings (SQL, JSON, HTML, etc.).

```java
String query = """
    SELECT u.id, u.name
    FROM users u
    WHERE u.active = true
    ORDER BY u.name
    """;
```

### `var` for Local Variables

Use `var` for local variables when the type is obvious from the right-hand side. Do not use `var` when it reduces readability.

```java
// CORRECT — type is obvious
var users = userRepository.findAll();
var mapper = new ObjectMapper();

// WRONG — type is not obvious, use explicit type
var result = process(data);
```

### Record Patterns (Java 21)

Destructure records directly in `instanceof` and `switch` expressions.

```java
// CORRECT
if (obj instanceof Point(int x, int y)) {
    System.out.println(x + ", " + y);
}

String desc = switch (shape) {
    case Circle(double r) -> "Circle r=" + r;
    case Rect(double w, double h) -> "Rect " + w + "x" + h;
};

// WRONG — old style: cast + field access
if (obj instanceof Point p) {
    System.out.println(p.x() + ", " + p.y());
}
```

### Unnamed Variables (Java 22)

Use `_` for unused variables, catch parameters, and lambda parameters.

```java
// CORRECT
try { ... } catch (Exception _) { ... }
try (var _ = ScopedValue.where(KEY, value)) { ... }
list.stream().map(_ -> "constant").toList();

// WRONG
try { ... } catch (Exception ignored) { ... }
try { ... } catch (Exception e) { ... } // when e is never used
```

### Immutable Collections

Use factory methods for immutable collections. Never use `Arrays.asList()` or `new ArrayList<>()` for fixed collections.

```java
// CORRECT
var roles = List.of("ADMIN", "USER");
var config = Map.of("timeout", 30, "retries", 3);
var tags = Set.of("java", "backend");

// WRONG
var roles = Arrays.asList("ADMIN", "USER");
var roles = new ArrayList<>(List.of("ADMIN", "USER"));
var roles = Collections.unmodifiableList(Arrays.asList("ADMIN", "USER"));
```

### Sequenced Collections (Java 21)

Use `getFirst()`, `getLast()`, `reversed()` instead of index arithmetic.

```java
// CORRECT
var first = list.getFirst();
var last = list.getLast();
var rev = list.reversed();

// WRONG
var first = list.get(0);
var last = list.get(list.size() - 1);
var rev = new ArrayList<>(list); Collections.reverse(rev);
```

### String Utilities (Java 11+)

Use modern `String` methods. Do not reimplement what the standard library already provides.

```java
// CORRECT
str.isBlank()               // replaces str.trim().isEmpty()
str.strip()                 // Unicode-aware trim
"ha".repeat(3)              // "hahaha"
"Hello %s".formatted(name) // replaces String.format(...)
str.lines()                 // Stream<String> of lines

// WRONG
str.trim().isEmpty()
String.format("Hello %s", name)
```

## Null Safety

**Nulls are not acceptable inside the system.** Things are non-null by default. Do not let nulls propagate beyond system boundaries (external APIs, database results, user input).

- **Do NOT annotate everything with `@NonNull`** — that pollutes the code. Instead, treat everything as non-null by default.
- **Use `@Nullable`** only when something genuinely can be null (rare, at system edges).
- If you receive null from an external source, convert it at the boundary: to an `Optional`, a default value, or throw early.
- **Never pass `null` as a method argument.** Never return `null` from a method. Use `Optional` for methods that may have no result.

```java
// CORRECT — Optional for methods that may return nothing
public Optional<User> findByEmail(String email) {
    return Optional.ofNullable(repository.get(email));
}

// WRONG — returning null
public User findByEmail(String email) {
    return repository.get(email); // might return null
}
```

## Optional

- Use `Optional` as return type for public methods that may have no result. It communicates intent: "this method might return nothing, and you must handle it."
- **Never** use `Optional` as a field type, method parameter, or in collections.
- **Never** call `Optional.get()` without checking. Use `orElseThrow()`, `orElse()`, `map()`, `flatMap()`, `ifPresent()`.

```java
// CORRECT
userService.findByEmail(email)
    .map(User::username)
    .orElseThrow(() -> new UserNotFoundException(email));

// WRONG
User user = userService.findByEmail(email).get();
```

## Error Handling

**Use Java's exception mechanism.** Do not try to turn Java into Go or C with error return codes or `Result` wrapper types. Java has checked and unchecked exceptions — use them.

- Use **unchecked exceptions** (extending `RuntimeException`) for programming errors and unexpected failures.
- Use **checked exceptions** sparingly, only when the caller genuinely must handle the failure and there is a reasonable recovery path.
- **Never swallow exceptions.** Always log or rethrow.
- **Never catch `Exception` or `Throwable`** generically unless at the top-level error boundary (e.g., controller advice, global handler).
- Use specific exception types. Create custom domain exceptions when the standard ones do not fit.
- Use **multi-catch** to avoid duplicated catch blocks.

```java
// CORRECT
catch (IOException | SQLException e) {
    log.error("Data access failed", e);
}

// WRONG — duplicated handlers
catch (IOException e) { log.error("...", e); }
catch (SQLException e) { log.error("...", e); }
```

```java
// CORRECT
public User getUser(UserId id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}

// WRONG — returning null on error
public User getUser(UserId id) {
    try {
        return userRepository.findById(id).orElse(null);
    } catch (Exception e) {
        return null;
    }
}
```

## Naming Conventions

- **Classes**: `PascalCase` — nouns (`UserService`, `PaymentProcessor`).
- **Methods**: `camelCase` — verbs (`findByEmail`, `processPayment`, `calculateTotal`).
- **Variables**: `camelCase` — nouns (`userId`, `orderItems`).
- **Constants**: `UPPER_SNAKE_CASE` (`MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT`).
- **Packages**: lowercase, dot-separated (`com.company.project.domain.user`).
- **Booleans**: prefix with `is`, `has`, `should`, `can` (`isValid()`, `hasPermission()`, `shouldRetry()`).
- **Collections**: plural names (`users`, `orders`, `products`).
- **No abbreviations** in public APIs. `userRepository` not `userRepo`. `transactionManager` not `txMgr`.
- Methods should not have surprising side effects. The name must accurately describe what the method does.

## Code Style & Formatting

- Use **one consistent style** across the project: either standard Java Coding Conventions or IntelliJ defaults. The specific choice does not matter — consistency does.
- Keep classes **package-private by default**. Only expose what is needed as `public`.
- Apply `final` to variables when immutability is intended.
- Use method references over lambdas when possible: `User::userId` instead of `u -> u.userId()`.
- Organize imports: standard library, third-party, internal packages. No wildcard imports.
- **If the project includes Spotless**, run the formatter after every code change (only for edited files). For Gradle: `./gradlew spotlessApply`. For Maven: `mvn spotless:apply`.

## Streams & Collections

Use Java Streams for collection transformations. Prefer `filter()`, `map()`, `flatMap()`, `toList()`.

```java
// CORRECT
var activeEmails = users.stream()
    .filter(User::isActive)
    .map(User::email)
    .toList();

// WRONG — manual loop for simple transformation
var activeEmails = new ArrayList<String>();
for (User user : users) {
    if (user.isActive()) {
        activeEmails.add(user.getEmail());
    }
}
```

Never perform side effects in intermediate operations (like `map`, `filter`, `flatMap`) — these should be pure functions. Terminal `forEach` is the right place for side effects. Prefer `Iterable.forEach()` over `stream().forEach()` when no intermediate operations are needed.

```java
// WRONG — side effect in intermediate operation
users.stream()
    .map(user -> { emailService.send(user); return user.email(); })
    .toList();

// WRONG — unnecessary stream() when there is no pipeline
users.stream().forEach(user -> emailService.send(user));

// CORRECT — Iterable.forEach for simple side effects
users.forEach(user -> emailService.send(user));

// CORRECT — pure filter, side effect only in terminal forEach
users.stream()
    .filter(User::isActive)
    .forEach(user -> emailService.send(user));

// CORRECT — plain loop when you need break/continue or checked exceptions
for (var user : users) {
    emailService.send(user);
}
```

## Testing

### Consistent Test Naming

Pick **one** naming convention for the entire project and enforce it. Recommended format:

```
methodName_shouldExpectedBehavior_whenCondition
```

Examples:
```java
@Test
void findByEmail_shouldReturnUser_whenUserExists() { ... }

@Test
void findByEmail_shouldThrowNotFoundException_whenUserDoesNotExist() { ... }

@Test
void processPayment_shouldReturnSuccess_whenBalanceSufficient() { ... }
```

Do NOT mix `camelCase`, `snake_case`, `given/when/then`, and `should` styles in the same project. Consistency matters more than the specific convention.

### Test Structure

- Use **JUnit 5** as the default testing framework.
- **Spock Framework** (Groovy-based) is an acceptable alternative if the team prefers BDD-style specifications. It is powerful but requires Groovy proficiency.
- Follow **Arrange-Act-Assert** (or Given-When-Then in Spock).
- Each test should test one behavior. No multi-assert tests covering unrelated behaviors.
- Use `@DisplayName` sparingly — a well-named test method is self-documenting.

### TDD

Test-Driven Development produces better APIs. Writing tests first forces you to design the public interface before the implementation. Even if not doing strict TDD, write tests immediately after writing the method signature and before the implementation body.

### Test Independence

Tests must be independent and repeatable. No shared mutable state between tests. No ordering dependencies.

## Concurrency

### Virtual Threads (Project Loom)

Use Virtual Threads for I/O-bound concurrent work. They eliminate the need for reactive frameworks for most use cases.

```java
// CORRECT — Virtual Threads
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var future = executor.submit(() -> fetchData(url));
    return future.get();
}

// WRONG — reactive chains for simple I/O
Mono.fromCallable(() -> fetchData(url))
    .subscribeOn(Schedulers.boundedElastic())
    .flatMap(data -> process(data))
    .subscribe();
```

### Immutability Enables Safe Concurrency

Writing proper OOP with small, immutable objects in small methods with limited scope is the best concurrency strategy. Small objects are created and garbage-collected quickly, reducing contention.

### Structured Concurrency (Java 25)

Use `StructuredTaskScope` to manage lifetimes of forked subtasks as a unit. Ensures that subtasks are reliably cancelled and joined when the scope closes.

```java
// CORRECT
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var user = scope.fork(() -> fetchUser(id));
    var order = scope.fork(() -> fetchOrder(id));
    scope.join().throwIfFailed();
    return new Response(user.get(), order.get());
}

// WRONG — manual executor management, no automatic lifecycle
var executor = Executors.newFixedThreadPool(2);
var f1 = executor.submit(() -> fetchUser(id));
var f2 = executor.submit(() -> fetchOrder(id));
executor.shutdown();
return new Response(f1.get(), f2.get());
```

### What NOT to Use

- **Do NOT use RxJava.** It is overly complex, hard to debug, and unnecessary with Virtual Threads. If you are in a project that uses RxJava, do not introduce more of it.
- Avoid `CompletableFuture` chains when Virtual Threads can express the same logic sequentially.
- Do not prematurely introduce reactive patterns. Use them only when you have proven back-pressure requirements.

## Modern I/O

Use `Files` convenience methods and `Path.of()` factory. Avoid `BufferedReader`/`Writer` boilerplate for simple operations.

```java
// CORRECT
String content = Files.readString(Path.of("config.json"));
Files.writeString(Path.of("output.txt"), data);
var path = Path.of("src", "main", "java");

// WRONG
var br = new BufferedReader(new FileReader("config.json"));
// ... manual read loop ...
var path = Paths.get("src", "main", "java");
```

Use the built-in `HttpClient` for HTTP calls. Do not add `HttpURLConnection` boilerplate.

```java
// CORRECT
var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder(URI.create(url)).GET().build();
var response = client.send(request, HttpResponse.BodyHandlers.ofString());

// WRONG — HttpURLConnection with manual stream handling
var conn = (HttpURLConnection) new URL(url).openConnection();
conn.setRequestMethod("GET");
var reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
// ...
```

## Date and Time

Always use `java.time` (Java 8+). Never use `Date`, `Calendar`, or `SimpleDateFormat`.

```java
// CORRECT
LocalDate today = LocalDate.now();
LocalDate date = LocalDate.of(2025, Month.JANUARY, 15);
Instant now = Instant.now();
long days = ChronoUnit.DAYS.between(start, end);
Duration duration = Duration.ofMinutes(30);

// WRONG
Date date = new Date();
Calendar cal = Calendar.getInstance();
cal.set(2025, 0, 15); // zero-indexed months
```

## Logging

- Use **SLF4J** (`org.slf4j.Logger`). Never use `System.out.println()` or `System.err.println()`.
- Use **parameterized logging** — never string concatenation.

```java
// CORRECT
log.info("Processing order {} for user {}", orderId, userId);

// WRONG
log.info("Processing order " + orderId + " for user " + userId);
```

- Log levels:
    - `error` — exceptions and failures requiring attention.
    - `warn` — recoverable issues, degraded behavior.
    - `info` — significant business events (order placed, payment processed).
    - `debug` — detailed technical information for troubleshooting.
    - `trace` — very fine-grained, typically disabled in production.

## Project Structure

- Separate **domain code from infrastructure code** in the package structure.
- Keep configuration classes in dedicated `config` packages.
- Follow consistent package naming: `com.company.project.<domain>.<layer>` (e.g., `com.acme.shop.order.repository`).
- One public class per file. No multi-class files except for tightly coupled inner classes.

## JVM & GC

- **Do not tune GC parameters unless you have measured a problem.** The default GC (G1GC in modern JVMs) is good enough for the vast majority of applications.
- Writing proper OOP with small, short-lived objects is better than any GC tuning.
- Profile before optimizing. Use JFR (Java Flight Recorder) and JMC (Java Mission Control) for performance analysis.

## Deprecated Patterns — Do NOT Use

These patterns are outdated and must not be used in new code:

| Deprecated Pattern | Modern Replacement |
|---|---|
| Lombok `@Value`, `@Data`, `@Getter`, `@Setter` for data classes | `record` |
| `Arrays.asList()` | `List.of()` |
| `new ArrayList<>(Arrays.asList(...))` | `List.of()` or `new ArrayList<>(List.of(...))` |
| `Collections.unmodifiableList()` | `List.copyOf()` or `List.of()` |
| RxJava / Project Reactor for simple I/O | Virtual Threads |
| Old `switch` statements with `break` | Switch expressions |
| Manual `instanceof` + cast | Pattern matching `instanceof` |
| String concatenation with `+` for multi-line | Text blocks |
| Anonymous inner classes for functional interfaces | Lambdas / method references |
| `synchronized` blocks for simple cases | `java.util.concurrent` locks or Virtual Threads |
| Returning `null` from methods | `Optional` or throw exception |
| `System.out.println()` | SLF4J logging |
| Raw types (`List` instead of `List<String>`) | Always use generics |
| `Date`, `Calendar` | `java.time` API (`LocalDate`, `Instant`, etc.) |
| `StringBuffer` | `StringBuilder` (or text blocks / `formatted()`) |
| `str.trim().isEmpty()` | `str.isBlank()` |
| `String.format(...)` | `"...".formatted(...)` |
| `list.get(list.size() - 1)` | `list.getLast()` |
| `Collections.reverse(new ArrayList<>(list))` | `list.reversed()` |
| `catch (Exception ignored)` | `catch (Exception _)` |
| Duplicate `catch` blocks | Multi-catch `catch (A \| B e)` |
| `BufferedReader` + loop for file reading | `Files.readString(Path.of(...))` |
| `Paths.get(...)` | `Path.of(...)` |
| `HttpURLConnection` | `HttpClient` (Java 11) |
| Manual executor lifecycle for structured tasks | `StructuredTaskScope` (Java 25) |
