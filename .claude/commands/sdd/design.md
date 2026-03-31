---
name: "sdd:design"
description: "Plan 문서를 상세 Design 문서로 변환하고 .bkit design 단계로 전환합니다.
  '설계 문서', '상세 설계', 'plan을 design으로', '클래스 설계', 'API 설계', '아키텍처 설계',
  'design', 'detailed design', 'class diagram', 'API contract', 'architecture design' 등의 요청에 반응합니다."
---

# sdd:design — .bkit design (phase 2)

Plan 문서를 받아 상세 Design 문서를 작성하고 `.bkit` design 단계로 전환합니다.

Input: $ARGUMENTS
(Plan 문서 경로, e.g. `docs/01-plan/features/issue-status-transition.plan.md`)

---

## Step 1 — Plan 문서 읽기

- `$ARGUMENTS`에서 Plan 문서 경로 파싱, `<slug>` 추출
- `docs/conventions/CONVENTIONS.md` 읽기 — 레이어 규칙, 패키지 구조 파악
- 기존 Bounded Context 코드 탐색 (`src/main/java/com/example/worker/`) — 네이밍 일관성 확보

---

## Step 2 — Design 문서 작성

출력 경로: `docs/02-design/features/<slug>.design.md`

문서 구조:

```markdown
# [Design] <기능명>

## Executive Summary
| 관점 | 내용 |
|------|------|
| **Problem** | Plan에서 가져온 문제 요약 |
| **Solution** | 설계 접근 방식 |
| **Core Value** | 핵심 가치 |

## 1. 변경 범위

### 신규 파일
| 레이어 | 파일 경로 | 역할 |
|--------|-----------|------|
| `domain/model/` | `...java` | ... |
| `application/service/` | `...java` | ... |
| `application/port/` | `...java` | ... |
| `infrastructure/datasource/` | `...java` | ... |
| `api/controller/` | `...java` | ... |

### 수정 파일
| 파일 | 변경 내용 |
|------|-----------|
| `...java` | ... |

## 2. 도메인 설계

### 2-1. 도메인 모델 (Aggregate / Value Object / Enum)

```java
// 각 클래스의 핵심 필드, 생성 팩토리, 상태 전이 메서드만 기술
// 비즈니스 로직 포함 위치 명시
```

### 2-2. 도메인 이벤트

```java
// event record 정의
// 발행 시점, 소비자
```

## 3. 애플리케이션 레이어 설계

### Command / Query 분리 (CQRS)

| 타입 | 서비스 클래스 | 입력 | 출력 | 부작용 |
|------|-------------|------|------|--------|
| Command | `XxxService` | `XxxRequest` | `void` / ID | DB 저장, 이벤트 발행 |
| Query   | `XxxService` | 파라미터 | `XxxResponse` | 없음 |

### 포트 인터페이스

```java
// application/port/*.java
```

## 4. API 설계

| Method | Path | Request Body | Response | Status |
|--------|------|-------------|----------|--------|
| POST | `/api/v1/...` | `XxxRequest` | `XxxResponse` | 201 |

### Request / Response DTO (record)

```java
// record 타입으로 정의
// compact constructor 검증 로직 포함 시 명시
```

## 5. 인프라 설계

### JPA Entity

```java
// @Entity, @Table, 핵심 필드, 매핑 전략
```

### Kafka (해당 시)

| 토픽 | Producer | Consumer | 페이로드 |
|------|---------|---------|---------|

## 6. 시퀀스 다이어그램

```
Client → Controller → Service → Repository → DB
                             ↘ EventPublisher → Kafka
```

## 7. 오류 처리

| 상황 | 예외 클래스 | HTTP 상태 | 메시지 |
|------|------------|----------|--------|

## 8. Open Questions
- [ ] TBD: ...

## 9. Related Docs
- Plan: `docs/01-plan/features/<slug>.plan.md`
- Skeleton 예정: `src/main/java/com/example/worker/<context>/`
```

---

## Step 3 — .bkit 상태 업데이트

문서 작성 완료 후 아래 파일들을 업데이트합니다.

**`.bkit/state/pdca-status.json`** — `features.<slug>` 블록 업데이트:
```json
{
  "phase": "design",
  "phaseNumber": 2,
  "matchRate": null,
  "documents": {
    "plan":   "docs/01-plan/features/<slug>.plan.md",
    "design": "docs/02-design/features/<slug>.design.md"
  },
  "timestamps": {
    "lastUpdated": "<현재 ISO 8601 타임스탬프>"
  },
  "lastFile": "docs/02-design/features/<slug>.design.md"
}
```

`lastUpdated` (루트 레벨) 갱신.

**`.bkit/state/memory.json`** 업데이트:
```json
{
  "currentFeature": "<slug>",
  "currentPhase": "design"
}
```

---

## Skill Connection

```
/sdd:requirements  →  /sdd:design  →  [현재 단계]  →  /sdd:skeleton
```

완료 메시지 예시:
```
✅ [Design] <기능명> 작성 완료
📄 docs/02-design/features/<slug>.design.md
🔄 .bkit: design (phase 2) 전환
➡️  다음: /sdd:skeleton docs/02-design/features/<slug>.design.md
```
