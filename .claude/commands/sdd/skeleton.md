---
name: "sdd:skeleton"
description: "Design 문서에서 컴파일 가능한 DDD 코드 스켈레톤을 생성하고 .bkit do 단계로 전환합니다.
  '스켈레톤 생성', '뼈대 코드', '코드 틀 만들기', '구조 생성', '패키지 구조',
  'skeleton', 'scaffold', 'generate structure', 'boilerplate', 'code stub' 등의 요청에 반응합니다."
---

# sdd:skeleton — .bkit do (phase 3)

Design 문서를 받아 컴파일 가능한 코드 스켈레톤을 생성하고 `.bkit` do 단계로 전환합니다.

Input: $ARGUMENTS
(Design 문서 경로, e.g. `docs/02-design/features/issue-status-transition.design.md`)

---

## Step 1 — Design 문서 읽기

- `$ARGUMENTS`에서 Design 문서 경로 파싱, `<slug>` 추출
- `docs/conventions/CONVENTIONS.md` 읽기 — 레이어 규칙 파악
- Design 문서의 **변경 범위 / 도메인 설계 / API 설계** 섹션에서 생성할 클래스 목록 추출

---

## Step 2 — 스켈레톤 생성 규칙

기본 패키지 루트: `src/main/java/com/example/worker/`

### 레이어별 생성 위치

| 레이어 | 경로 패턴 | 허용 어노테이션 |
|--------|-----------|----------------|
| Domain Model | `<context>/domain/model/` | 없음 (순수 Java) |
| Domain Event | `<context>/event/model/` | `record` |
| Application Port | `<context>/application/port/` | `interface` |
| Application Service | `<context>/application/service/` | `@Service` |
| Infrastructure Adapter | `<context>/infrastructure/datasource/` | `@Repository` |
| API Controller | `<context>/api/controller/` | `@RestController` |
| API Request/Response | `<context>/api/request/`, `<context>/api/response/` | `record` |

### 스켈레톤 코드 규칙

1. **비즈니스 로직 구현 금지** — 메서드 본문은 `throw new UnsupportedOperationException("not implemented")` 또는 `return null`
2. **Record 우선** — 불변 DTO, VO, 이벤트는 `record` 타입으로 생성
3. **인터페이스 우선** — 포트, 서비스는 인터페이스 + 구현체 분리
4. **Lombok 금지** — `@Data`, `@Getter`, `@Setter` 사용 금지. `@Builder` 허용.
5. **매직 상수 금지** — 상수는 `enum` 또는 `static final`로 선언
6. **컴파일 가능** — import 포함, 문법 오류 없는 stub 코드

### 예시 패턴

```java
// ✅ Domain Model — 행위와 상태를 가진 객체
public class Issue {
    private final UUID id;
    private IssueStatus status;

    private Issue(UUID id, IssueStatus status) { ... }

    public static Issue create(UUID projectId, String title) {
        throw new UnsupportedOperationException("not implemented");
    }

    public void startProgress() {
        throw new UnsupportedOperationException("not implemented");
    }
}

// ✅ Event — record 타입
public record IssueStatusChangedEvent(UUID issueId, IssueStatus newStatus, Instant occurredAt) {
    public static IssueStatusChangedEvent of(UUID issueId, IssueStatus status) {
        throw new UnsupportedOperationException("not implemented");
    }
}

// ✅ Port — interface
public interface IssueRepository {
    Issue save(Issue issue);
    Optional<Issue> findById(UUID id);
}

// ✅ Request — record with validation
public record CreateIssueRequest(
    @NotBlank String title,
    @NotNull UUID projectId
) {}
```

---

## Step 3 — .bkit 상태 업데이트

스켈레톤 생성 완료 후 아래 파일들을 업데이트합니다.

**`.bkit/state/pdca-status.json`** — `features.<slug>` 블록 업데이트:
```json
{
  "phase": "do",
  "phaseNumber": 3,
  "documents": {
    "plan":   "docs/01-plan/features/<slug>.plan.md",
    "design": "docs/02-design/features/<slug>.design.md"
  },
  "timestamps": {
    "lastUpdated": "<현재 ISO 8601 타임스탬프>"
  },
  "lastFile": "src/main/java/com/example/worker/<context>/<마지막 생성 파일>"
}
```

**`.bkit/state/memory.json`** 업데이트:
```json
{
  "currentFeature": "<slug>",
  "currentPhase": "do"
}
```

---

## Skill Connection

```
/sdd:design  →  /sdd:skeleton  →  [현재 단계]  →  /sdd:tests
```

완료 메시지 예시:
```
✅ [Skeleton] <기능명> 스켈레톤 생성 완료
📁 생성된 파일 목록
🔄 .bkit: do (phase 3) 전환
➡️  다음: /sdd:tests docs/02-design/features/<slug>.design.md src/main/java/com/example/worker/<context>
```
