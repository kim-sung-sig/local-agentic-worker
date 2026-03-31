---
name: "sdd:review"
description: "Design 문서, 코드, 테스트의 컨벤션 준수 및 스펙 정합성을 검토하고 .bkit check 단계로 전환합니다.
  'SDD 검토', '설계 대조', '구현 검증', '스펙 리뷰', '컨벤션 확인', 'matchRate',
  'spec review', 'verify implementation', 'check against design', 'compliance review' 등의 요청에 반응합니다."
---

# sdd:review — .bkit check (phase 4)

Design 문서, 코드, 테스트를 교차 검토하고 matchRate를 계산하여 `.bkit` check 단계로 전환합니다.

Input: $ARGUMENTS
(Design 문서 경로 + 코드 경로 + 테스트 경로,
 e.g. `docs/02-design/features/issue-status-transition.design.md src/main/java/com/example/worker/issue src/test/java/com/example/worker/issue`)

---

## Step 1 — 입력 읽기

- Design 문서 읽기: 변경 범위, 요구사항, API 계약, 오류 처리 추출
- 코드 경로 탐색: 생성된 파일 목록과 실제 구현 내용 파악
- 테스트 경로 탐색: 테스트 파일 목록과 케이스 수 파악
- `docs/conventions/CONVENTIONS.md` 읽기: 컨벤션 기준 파악

---

## Review Checklist

### Plan/Design 완성도
- [ ] Design 문서의 모든 섹션 작성 완료 (또는 `TBD:` 명시)
- [ ] Open Questions 항목 처리 여부 확인
- [ ] 도메인 언어가 기존 Bounded Context와 일치

### 코드 컨벤션 (CONVENTIONS.md 기준)
- [ ] DDD 레이어 경계 준수 (domain → application → infrastructure → api)
- [ ] 도메인 모델에 비즈니스 로직 위치 (Anemic 도메인 금지)
- [ ] Record 타입 활용 (DTO, Value Object, Event)
- [ ] Lombok `@Data/@Getter/@Setter` 미사용
- [ ] 매직 상수 없음 (enum 또는 static final 사용)
- [ ] `new` 키워드 최소화 — 팩토리 메서드 또는 DI 사용
- [ ] 인터페이스 기반 포트 설계 (DIP 준수)
- [ ] 서비스 계층 오케스트레이션만 수행 (비즈니스 로직 없음)

### 스펙 정합성 (Design ↔ 코드)
- [ ] Design의 신규 파일 목록 모두 생성됨
- [ ] Design의 수정 파일 목록 모두 변경됨
- [ ] API 엔드포인트, Request/Response 타입 일치
- [ ] 도메인 이벤트 발행 시점 일치
- [ ] 오류 처리 (예외 클래스, HTTP 상태코드) 일치

### 테스트 커버리지 (Design ↔ 테스트)
- [ ] Design의 각 기능 요구사항(FR)에 대응하는 테스트 존재
- [ ] HappyPath / Failure / Boundary 케이스 커버
- [ ] `@DisplayName` 한국어 작성
- [ ] Given/When/Then 구조 준수
- [ ] 도메인/서비스 테스트 우선 (API 테스트 최소화)
- [ ] Mockito `then(mock).should()` 협력 검증 포함

---

## Step 2 — matchRate 계산

전체 체크리스트 항목 중 통과한 비율을 계산합니다.

```
matchRate = (통과 항목 수 / 전체 항목 수) × 100
```

등급 기준:
| matchRate | 등급 | 의미 |
|-----------|------|------|
| 90~100 | 🟢 PASS | 구현 완료, do 단계 종료 가능 |
| 70~89  | 🟡 CONDITIONAL | 주요 이슈 수정 후 재검토 |
| 0~69   | 🔴 FAIL | 재구현 필요 |

---

## Step 3 — 리뷰 리포트 출력

```markdown
## Review Report — <기능명>

| 항목 | 결과 |
|------|------|
| matchRate | {N}% ({등급}) |
| Design 완성도 | PASS / FAIL |
| 코드 컨벤션 | PASS / FAIL |
| 스펙 정합성 | PASS / FAIL |
| 테스트 커버리지 | PASS / FAIL |

### Findings
| 심각도 | 위치 | 설명 |
|--------|------|------|
| BLOCKER | `...java:L42` | ... |
| MAJOR   | `...java:L15` | ... |
| MINOR   | `...java:L88` | ... |

### Missing Coverage
- ...

### Recommendations
- ...
```

---

## Step 4 — .bkit 상태 업데이트

리뷰 완료 후 아래 파일들을 업데이트합니다.

**`.bkit/state/pdca-status.json`** — `features.<slug>` 블록 업데이트:
```json
{
  "phase": "check",
  "phaseNumber": 4,
  "matchRate": <계산된 숫자, e.g. 87>,
  "timestamps": { "lastUpdated": "<현재 ISO 8601 타임스탬프>" },
  "lastFile": "docs/02-design/features/<slug>.design.md"
}
```

**`.bkit/state/memory.json`** 업데이트:
```json
{
  "currentFeature": "<slug>",
  "currentPhase": "check"
}
```

---

## Skill Connection

```
/sdd:tests  →  /sdd:review  →  [현재 단계]  →  (matchRate ≥ 90: 완료 | 미만: /sdd:skeleton 재실행)
```

완료 메시지 예시:
```
✅ [Review] <기능명> 리뷰 완료
📊 matchRate: 87% (🟡 CONDITIONAL)
🔄 .bkit: check (phase 4) 전환
⚠️  BLOCKER 0건 / MAJOR 2건 수정 권장
➡️  수정 후: /sdd:review 재실행 또는 완료 처리
```
