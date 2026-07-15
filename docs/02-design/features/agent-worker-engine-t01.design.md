# [Design] Agent Worker Engine — T01 Temporal Foundation

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 6단계 durable 워크플로를 구동할 Temporal 기반이 없으면 T02 이후 어떤 Task도 시작할 수 없다 |
| **WHO** | agent-worker-engine을 이어서 구현할 개발자(에이전트) 자신 |
| **RISK** | Temporal SDK 버전을 느슨하게(`1.+`) 고정하면 이후 Task에서 breaking change로 재작업 발생 |
| **SUCCESS** | `EngineHealthWorkflow.run()`이 Temporal을 통해 `"ok"`를 반환하고, Workflow 구현체에 비결정적 I/O가 전혀 없다 |
| **SCOPE** | T01만 포함 — Engine 상태 저장(T02), Activity 계약(T03) 등은 범위 밖 |

---

## 1. Overview

### 1.1 Design Goals

- Spring Boot 부트스트랩 시 Temporal Client/Worker를 안전하게 생성·기동한다.
- 연결 설정(namespace, target endpoint)의 보일러플레이트를 줄이되, Worker의 큐 이름·Workflow 등록은 `engine` BC 코드에서 명시적으로 제어한다.
- `EngineHealthWorkflow`는 순수 결정론적 코드만 포함하여 이후 실제 6단계 워크플로 구현의 기준선이 된다.

### 1.2 Design Principles

- **격리**: `engine` bounded context는 `agent`/`issue`/`project`와 완전히 독립 — 기존 코드 무변경.
- **명시적 배선**: Worker의 task queue, 등록되는 Workflow 목록은 코드에서 명시적으로 선언(annotation 스캔에만 의존하지 않음) — 테스트 용이성과 가독성 확보.
- **결정론 강제**: Workflow 구현체 파일에는 이번 Task는 물론 이후 Task에서도 Git/파일/CLI/DB/현재시각/난수 호출 코드가 없어야 한다는 규칙을 코드 리뷰 체크 항목으로 고정한다.

---

## 2. Architecture Options

### 2.0 Architecture Comparison

| Criteria | Option A: Minimal | Option B: Clean (official starter, 자동 등록) | Option C(선택): Pragmatic Hybrid |
|----------|:-:|:-:|:-:|
| **Approach** | `temporal-sdk`만 추가, 단일 `@Configuration`에서 stub/client/worker 전부 직접 생성, `@Value`로 프로퍼티 직접 주입 | `io.temporal:spring-boot-starter` 도입, `spring.temporal.*` 프로퍼티로 client 자동 구성 + `@WorkflowImpl` 컴포넌트 스캔으로 자동 등록 | starter로 client/connection 보일러플레이트는 줄이되, Worker 생성과 task queue 등록·Workflow 바인딩은 `TemporalConfiguration`에서 명시적으로 수행 |
| **New Files** | 3 | 3 | 4 (properties 바인딩 클래스 분리) |
| **Modified Files** | 2 | 2 | 2 |
| **Complexity** | Low | Medium | Medium |
| **Maintainability** | Medium (연결 설정과 등록 로직이 한 클래스에 혼재) | Medium (자동 등록이라 어떤 Workflow가 어느 큐에 등록됐는지 코드 상 추적이 어려움) | High (연결은 starter, 등록은 명시적 — 관심사 분리) |
| **Effort** | Low | Low | Medium |
| **Risk** | Low, 다만 T02+ 확장 시 설정 로직 재작성 필요 | Low, 다만 자동 등록 방식이 이후 Task에서 큐별 Worker 다중화 요구와 충돌 가능 | Low |
| **Recommendation** | 가장 빠르지만 이후 Task 확장성 부족 | 보일러플레이트는 최소지만 명시성 부족 | **채택 — 두 접근의 장점을 결합** |

**Selected**: Option C (Pragmatic Hybrid) — **Rationale**: 사용자가 Option B와 Option C를 함께 요청함에 따라, 연결(client) 계층은 공식 `io.temporal:spring-boot-starter`로 단순화하고, Worker/Workflow 등록은 `TemporalConfiguration`에서 명시적으로 수행하는 하이브리드로 확정한다. 이는 프로젝트의 기존 관례(수동 `@Configuration`, 명시적 빈 등록)와 정합하면서도 연결 문자열/타임아웃 등 반복 설정 코드를 줄인다.

### 2.1 Component Diagram

```
Spring Boot Application
  ├── spring.temporal.* (application.properties)
  │     └── io.temporal:spring-boot-starter 자동구성
  │           └── WorkflowServiceStubs / WorkflowClient (Bean, 자동 생성)
  │
  └── engine.infrastructure.temporal.TemporalConfiguration (수동 @Configuration)
        ├── WorkerFactory Bean (WorkflowClient 주입받아 생성)
        └── Worker("agent-worker-engine") 등록
              └── registerWorkflowImplementationTypes(EngineHealthWorkflowImpl.class)
```

### 2.2 Data Flow

```
Application 기동 → starter가 WorkflowClient 생성
  → TemporalConfiguration이 WorkerFactory 생성, "agent-worker-engine" 큐에 Worker 등록
  → WorkerFactory.start()
  → 클라이언트가 EngineHealthWorkflow.run() 호출
  → Temporal 서버가 task queue로 Task 전달 → Worker가 EngineHealthWorkflowImpl 실행 → "ok" 반환
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| `TemporalConfiguration` | `WorkflowClient` (starter가 제공하는 Bean) | WorkerFactory/Worker 생성, 큐 등록 |
| `EngineHealthWorkflowImpl` | 없음 (외부 의존성 없는 순수 로직) | 최소 결정론적 워크플로 |
| `EngineHealthWorkflowTest` | `TestWorkflowEnvironment` (temporal-testing) | 서버 없이 워크플로 동작 검증 |

---

## 3. Data Model

이번 Task는 영속 데이터 모델이 없다 (Engine 상태 저장은 T02 범위).

---

## 4. API Specification

이번 Task는 외부 노출 API가 없다. `EngineHealthWorkflow`는 내부 검증용 Workflow 인터페이스다.

```java
@WorkflowInterface
public interface EngineHealthWorkflow {
    @WorkflowMethod
    String run();
}
```

---

## 5. Error Handling

### 5.1 기동 시 실패 처리

| 상황 | 처리 |
|------|------|
| Temporal 서버(dev server)에 연결 불가 | Spring Boot 기동 로그에 연결 실패 기록, 애플리케이션은 계속 기동(선택적 재시도는 T04 이후 범위) — 이번 Task는 로컬 개발 서버 존재를 전제로 단위 테스트만 필수 검증 |
| 잘못된 namespace/queue 설정 | `temporal.*` 프로퍼티 바인딩 실패 시 Spring 기동 실패 (fail-fast) |

---

## 6. Security Considerations

- Temporal 연결 정보(`temporal.connection.target`, namespace)는 `application.properties`의 `temporal.*` 네임스페이스로 분리하여 Kafka 등 다른 인프라 설정과 섞이지 않는다.
- 이번 Task는 인증/TLS 설정을 포함하지 않는다(로컬 개발 서버 대상) — 운영 환경 mTLS/API Key 설정은 이후 Task에서 다룬다.

---

## 7. Test Plan

### 7.1 Test Scope

| Type | Target | Tool | Phase |
|------|--------|------|-------|
| Unit | `EngineHealthWorkflowImpl.run()` | `TestWorkflowEnvironment` (JUnit 5) | Do |
| Manual | Spring Boot 기동 + 로컬 Temporal dev server 대상 Worker 등록 확인 | 수동 확인 | Do |

### 7.2 Unit Test Scenario

| # | Target | Test Description | Expected Result |
|---|--------|-------------------|------------------|
| 1 | `EngineHealthWorkflow` | `TestWorkflowEnvironment`에 `EngineHealthWorkflowImpl` 등록 후 `run()` 호출 | 반환값이 정확히 `"ok"` |

### 7.3 Manual Verification

- 로컬 Temporal dev server(`temporal server start-dev`) 기동 후 애플리케이션 실행 시 `agent-worker-engine` 큐에 Worker가 정확히 1회 등록되는지 로그로 확인.

---

## 8. Clean Architecture

### 8.1 Layer Structure (engine bounded context)

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Workflow** | Temporal Workflow 인터페이스/구현 (결정론 전용) | `src/main/java/com/example/worker/engine/workflow/` |
| **Infrastructure** | Temporal Client/Worker 배선 | `src/main/java/com/example/worker/engine/infrastructure/temporal/` |

### 8.2 Dependency Rules

- `workflow` 패키지는 `infrastructure` 패키지를 참조하지 않는다 (Workflow는 순수 결정론 코드).
- `infrastructure.temporal.TemporalConfiguration`만 `workflow` 패키지의 구현 클래스를 참조하여 Worker에 등록한다.
- 기존 `agent`, `issue`, `project` 패키지는 이번 Task에서 참조하거나 수정하지 않는다.

---

## 9. Implementation Guide

### 9.1 File Structure

```
src/main/java/com/example/worker/engine/
├── infrastructure/
│   └── temporal/
│       └── TemporalConfiguration.java
└── workflow/
    ├── EngineHealthWorkflow.java
    └── EngineHealthWorkflowImpl.java

src/test/java/com/example/worker/engine/
└── workflow/
    └── EngineHealthWorkflowTest.java
```

### 9.2 Implementation Order

1. [ ] `build.gradle` — `io.temporal:temporal-sdk`(고정 버전) + `io.temporal:spring-boot-starter`(고정 버전) 추가, `test` 구성에 temporal-testing 추가
2. [ ] `application.properties` — `temporal.connection.target`, `temporal.namespace`, `temporal.worker.task-queue=agent-worker-engine` 등 `temporal.*` 프로퍼티 추가 (Kafka 설정과 분리)
3. [ ] `EngineHealthWorkflow` 인터페이스 정의
4. [ ] `EngineHealthWorkflowImpl` 구현 (결정론적 코드만 — `return "ok";` 수준)
5. [ ] `TemporalConfiguration` — starter가 제공하는 `WorkflowClient` 빈을 주입받아 `WorkerFactory` 생성, `agent-worker-engine` 큐에 `EngineHealthWorkflowImpl` 등록, `WorkerFactory.start()` 호출 (`SmartLifecycle` 또는 `@PostConstruct`/`@PreDestroy`로 시작·종료 관리)
6. [ ] `EngineHealthWorkflowTest` — `TestWorkflowEnvironment`로 워크플로 등록 후 `run()` 호출, `"ok"` assert

### 9.3 Session Guide

단일 세션으로 완결 가능한 범위(신규 파일 4개, 수정 파일 2개) — 별도 모듈 분할 불필요.

| Session | Phase | Scope | 비고 |
|---------|-------|-------|------|
| Session 1 | Do | 전체 (파일 6개) | 소규모 Task이므로 스코프 분할 없이 한 세션에서 구현 |

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-15 | Initial draft — Option C(Hybrid) 선택 | Claude |
