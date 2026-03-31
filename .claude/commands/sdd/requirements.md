---
name: "sdd:requirements"
description: "요구사항을 구조화된 Plan 문서로 변환하고 .bkit plan 단계를 시작합니다.
  '요구사항 분석', '기능 정의', '이슈를 SDD로', '플랜 작성', '스펙 작성', '피처 계획',
  'requirements', 'feature spec', 'plan', 'what to build', 'write spec', 'feature plan' 등의 요청에 반응합니다."
---

# sdd:requirements — .bkit plan (phase 1)

요구사항을 받아 Plan 문서를 작성하고 `.bkit` plan 단계를 시작합니다.

Input: $ARGUMENTS

---

## Step 1 — 요구사항 분석

입력에서 다음 요소를 추출합니다:

| 항목 | 질문 |
|------|------|
| Actor | 누가 이 기능을 사용하는가? |
| Trigger | 어떤 이벤트/액션이 시작점인가? |
| Invariant | 항상 지켜야 할 비즈니스 규칙은? |
| Domain Event | 결과로 발생하는 도메인 이벤트는? (과거형, e.g. `IssueStatusChanged`) |
| Error Case | 무엇이 잘못될 수 있는가? |

---

## Step 2 — Plan 문서 작성

`<slug>` = 기능명에서 kebab-case 짧은 이름 유도 (예: `issue-status-transition`)

출력 경로: `docs/01-plan/features/<slug>.plan.md`

문서 구조:

```markdown
# [Plan] <기능명>

## Executive Summary
| 관점 | 내용 |
|------|------|
| **Problem** | 해결하려는 문제 |
| **Solution** | 접근 방식 |
| **Core Value** | 핵심 가치 |

## 1. 요구사항

### 기능 요구사항 (FR)
- FR-01: ...

### 비기능 요구사항 (NFR)
- NFR-01: ...

## 2. 도메인 분석

### 도메인 이벤트
- `<EventName>`: 설명

### 관련 Bounded Context
- `<context>` 도메인: 역할

## 3. 작업 범위

### In Scope
- ...

### Out of Scope
- ...

## 4. Open Questions
- [ ] TBD: ...

## 5. Related Docs
- Design: `docs/02-design/features/<slug>.design.md` (예정)
```

---

## Step 3 — .bkit 상태 업데이트

문서 작성 완료 후 아래 파일들을 업데이트합니다.

**`.bkit/state/pdca-status.json`** — `features.<slug>` 블록 추가/업데이트:
```json
{
  "phase": "plan",
  "phaseNumber": 1,
  "matchRate": null,
  "iterationCount": 0,
  "requirements": [],
  "documents": {
    "plan": "docs/01-plan/features/<slug>.plan.md"
  },
  "timestamps": {
    "started": "<현재 ISO 8601 타임스탬프>",
    "lastUpdated": "<현재 ISO 8601 타임스탬프>"
  },
  "lastFile": "docs/01-plan/features/<slug>.plan.md"
}
```

`activeFeatures` 배열에 `<slug>` 추가, `primaryFeature` = `<slug>`, `lastUpdated` 갱신.

**`.bkit/state/memory.json`** 업데이트:
```json
{
  "currentFeature": "<slug>",
  "currentPhase": "plan"
}
```

---

## Skill Connection

```
/sdd:requirements  →  [현재 단계]  →  /sdd:design
```

완료 메시지 예시:
```
✅ [Plan] <기능명> 작성 완료
📄 docs/01-plan/features/<slug>.plan.md
🔄 .bkit: plan (phase 1) 시작
➡️  다음: /sdd:design docs/01-plan/features/<slug>.plan.md
```
