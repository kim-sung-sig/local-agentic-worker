---
name: "sdd:tests"
description: "Design 문서와 스켈레톤에서 TDD 테스트 코드를 생성합니다. .bkit do 단계에서 실행됩니다.
  '테스트 작성', 'TDD', '단위 테스트', '테스트 코드 생성', 'JUnit 테스트',
  'write tests', 'TDD', 'unit tests', 'test cases', 'generate tests' 등의 요청에 반응합니다."
---

# sdd:tests — .bkit do (phase 3, test step)

Design 문서와 스켈레톤을 받아 TDD 테스트 코드를 생성합니다.

Input: $ARGUMENTS
(Design 문서 경로 + 스켈레톤 패키지 경로,
 e.g. `docs/02-design/features/issue-status-transition.design.md src/main/java/com/example/worker/issue`)

---

## Step 1 — 입력 읽기

- Design 문서 읽기: 요구사항, 도메인 규칙, API 계약, 오류 처리 추출
- 스켈레톤 경로 탐색: 생성된 클래스/인터페이스 목록 파악
- `docs/conventions/CONVENTIONS.md` 읽기: 테스트 컨벤션 확인

---

## Step 2 — 테스트 매트릭스 구성

Design 문서의 각 요구사항 → 테스트 케이스 1:N 매핑

| 요구사항 ID | 대상 클래스 | 테스트 케이스 | 분류 |
|------------|------------|-------------|------|
| FR-01 | `IssueService` | 정상 상태 전환 | HappyPath |
| FR-01 | `IssueService` | 잘못된 상태에서 전환 시도 | Failure |
| NFR-01 | `IssueRepository` | ... | Boundary |

---

## Step 3 — 테스트 생성 규칙

기본 테스트 루트: `src/test/java/com/example/worker/`

### 테스트 구조 규칙

```java
@DisplayName("IssueService")
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private IssueService sut;  // sut = System Under Test

    @Nested
    @DisplayName("startProgress - 이슈 진행 시작")
    class StartProgress {

        @Test
        @DisplayName("정상 - OPEN 상태 이슈를 IN_PROGRESS로 전환한다")
        void success_opensIssueTransitionsToInProgress() {
            // Given
            var issue = Issue.create(...);

            // When
            sut.startProgress(issue.getId());

            // Then
            then(issueRepository).should().save(argThat(i ->
                i.getStatus() == IssueStatus.IN_PROGRESS
            ));
        }

        @Test
        @DisplayName("실패 - DONE 상태 이슈는 전환 불가 예외 발생")
        void fail_doneIssueThrowsException() {
            // Given
            // When / Then
            assertThatThrownBy(() -> sut.startProgress(...))
                .isInstanceOf(IssueException.class);
        }
    }
}
```

### 필수 규칙

1. `@DisplayName` — **모든** 클래스, Nested 클래스, 메서드에 한국어로 작성
2. 중첩 구조 — `@Nested` 클래스 = 메서드 단위, 내부 `HappyPath` / `Failure` / `Boundary` 분류
3. Mockito 사용 — `@Mock` + `@InjectMocks` (또는 `@ExtendWith(MockitoExtension.class)`)
4. **Given / When / Then** 구조 주석 필수
5. 경계값 테스트 — null, 빈 문자열, 최소/최대 길이 포함
6. `then(mock).should()` 패턴으로 협력 객체 상호작용 검증
7. AssertJ 사용 — `assertThat`, `assertThatThrownBy`
8. **도메인/서비스 테스트 우선** — API 테스트는 계약 검증 필요 시만

### 테스트 파일 배치

```
src/test/java/com/example/worker/
  <context>/
    domain/model/           ← 도메인 모델 단위 테스트
    application/service/    ← 서비스 단위 테스트 (Mockito)
    infrastructure/         ← 필요 시 통합 테스트
    api/controller/         ← @WebMvcTest 슬라이스 테스트 (선택)
```

---

## Step 4 — .bkit 상태 업데이트

테스트 생성 완료 후 `.bkit/state/pdca-status.json` 업데이트:
```json
{
  "phase": "do",
  "phaseNumber": 3,
  "timestamps": { "lastUpdated": "<현재 ISO 8601 타임스탬프>" },
  "lastFile": "src/test/java/com/example/worker/<context>/<마지막 테스트 파일>"
}
```

---

## Skill Connection

```
/sdd:skeleton  →  /sdd:tests  →  [현재 단계]  →  /sdd:review
```

완료 메시지 예시:
```
✅ [Tests] <기능명> 테스트 생성 완료
📁 생성된 테스트 파일 목록
🔢 테스트 케이스 수: N개
➡️  다음: /sdd:review docs/02-design/features/<slug>.design.md src/main/java/... src/test/java/...
```
