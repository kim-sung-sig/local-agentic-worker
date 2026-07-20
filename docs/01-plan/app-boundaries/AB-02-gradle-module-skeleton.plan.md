# AB-02: Gradle Module Skeleton

**Single responsibility:** Introduce `control-plane-app` and `agent-engine-app` as empty, independently
buildable Spring Boot Gradle subprojects. No domain code moves in this task — that is AB-03/AB-04.

**Commit message:** `chore: add control-plane-app and agent-engine-app modules`

## Why split this from the domain move

Moving every package in one commit conflates two risks: build-topology mistakes (wrong dependency,
wrong plugin) and domain-logic mistakes (a moved class losing a Spring annotation, a missed import).
Standing up two empty, bootable apps first isolates the topology risk and gives AB-03/AB-04 a known-good
target to move code into.

## Design

```
settings.gradle
  include 'contracts'
  include 'control-plane-app'
  include 'agent-engine-app'

control-plane-app/
  build.gradle              -- Spring Boot app: web, validation, data-jpa, flyway, postgresql, kafka
  src/main/java/.../ControlPlaneApplication.java
  src/main/resources/application.properties  -- server.port=18081 (keeps today's port; frontend unaffected)

agent-engine-app/
  build.gradle              -- Spring Boot app: web, validation, data-jpa, temporal-spring-boot-starter,
                               kafka (producer only, for EngineNotificationRequested)
  src/main/java/.../AgentEngineApplication.java
  src/main/resources/application.properties  -- server.port=18082 (new port)
```

Both subprojects declare `implementation project(':contracts')` and nothing else from each other. The
root project (`worker`) keeps all existing source untouched for now — it still builds and its tests
still pass; AB-03/AB-04 drain it into the two new modules.

## Steps

1. Add `control-plane-app` and `agent-engine-app` to `settings.gradle`.
2. Create each subproject's `build.gradle` with only the dependencies its future domains need (see
   above) — no Temporal in `control-plane-app`, no fuller Kafka consumer-side config in
   `agent-engine-app` beyond what publishing `EngineNotificationRequested` needs.
3. Create a minimal `@SpringBootApplication` main class in each, plus a placeholder
   `application.properties` (distinct `server.port`, `spring.application.name`).
4. Verify: `./gradlew :control-plane-app:build :agent-engine-app:build` succeeds (compiles, packages);
   `./gradlew build` at the root still succeeds unchanged (root app is untouched).
5. Commit only the new module scaffolding — no root-project or domain-package changes.

## Acceptance criteria

1. `./gradlew :control-plane-app:build` and `./gradlew :agent-engine-app:build` both succeed from a
   clean invocation.
2. Neither new module has a source dependency other than `contracts` and Spring Boot starters.
3. The root `worker` project's existing build and full test suite are unaffected (still green).
4. No domain package (`project`, `issue`, `notification`, `agent`, `engine`, `runtime`, `scm`) has
   moved yet.
