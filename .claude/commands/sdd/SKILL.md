---
name: sdd
description: "SDD(Software Design Document) 스킬체인의 진입점입니다. .bkit PDCA 플로우와 통합되어 requirements → design → skeleton → tests → review 순서로 진행합니다.
  'SDD', '설계 문서 작성', '소프트웨어 설계', '스펙 작성', '설계 체인',
  'software design document', 'SDD chain', 'spec', 'design doc' 등의 요청에 반응합니다."
---

# SDD 스킬체인 — .bkit PDCA 통합 버전

`$ARGUMENTS` 없이 호출 시 이 개요를 표시합니다.

---

## SDD ↔ .bkit PDCA 매핑

```
/sdd:requirements  →  .bkit plan   (phase 1)  →  docs/01-plan/features/<slug>.plan.md
/sdd:design        →  .bkit design (phase 2)  →  docs/02-design/features/<slug>.design.md
/sdd:skeleton      →  .bkit do     (phase 3)  →  src/ 코드 스텁
/sdd:tests         →  .bkit do     (phase 3)  →  src/test/ 테스트 파일
/sdd:review        →  .bkit check  (phase 4)  →  matchRate 기록 + 리뷰 리포트
```

각 단계 완료 시 `.bkit/state/pdca-status.json` 및 `.bkit/state/memory.json`이 자동 업데이트됩니다.

---

## 커맨드 목록

| 커맨드 | .bkit 페이즈 | 입력 | 출력 |
|--------|-------------|------|------|
| `/sdd:requirements` | plan (1) | 요구사항 텍스트 | `docs/01-plan/features/<slug>.plan.md` |
| `/sdd:design` | design (2) | plan 문서 경로 | `docs/02-design/features/<slug>.design.md` |
| `/sdd:skeleton` | do (3) | design 문서 경로 | `src/` 코드 스텁 |
| `/sdd:tests` | do (3) | design 문서 + 스텁 경로 | `src/test/` 테스트 파일 |
| `/sdd:review` | check (4) | design + 코드 + 테스트 경로 | 리뷰 리포트 + matchRate |
| `/sdd:status` | — | feature slug (선택) | .bkit 현재 상태 표시 |

---

## 빠른 시작

```bash
# 1. 요구사항 → plan 문서 + .bkit plan 단계 시작
/sdd:requirements "이슈 상태 자동 전환 - Kafka 이벤트 수신 시 이슈 상태를 OPEN→IN_PROGRESS 변경"

# 2. plan → design 문서 + .bkit design 단계 전환
/sdd:design docs/01-plan/features/issue-status-transition.plan.md

# 3. design → 코드 스텁 + .bkit do 단계 전환
/sdd:skeleton docs/02-design/features/issue-status-transition.design.md

# 4. design + 스텁 → TDD 테스트
/sdd:tests docs/02-design/features/issue-status-transition.design.md src/main/java/com/example/worker/issue

# 5. 전체 리뷰 + .bkit check 단계 + matchRate 기록
/sdd:review docs/02-design/features/issue-status-transition.design.md src/main/java/com/example/worker/issue src/test/java/com/example/worker/issue

# 현재 .bkit 상태 확인
/sdd:status
/sdd:status issue-status-transition
```

---

## .bkit 상태 파일 구조 참고

```json
// .bkit/state/pdca-status.json — features.<slug> 블록
{
  "phase": "plan|design|do|check|act",
  "phaseNumber": 1,
  "matchRate": null,
  "documents": {
    "plan":   "docs/01-plan/features/<slug>.plan.md",
    "design": "docs/02-design/features/<slug>.design.md"
  },
  "timestamps": { "started": "...", "lastUpdated": "..." },
  "lastFile": "docs/01-plan/features/<slug>.plan.md"
}

// .bkit/state/memory.json
{ "currentFeature": "<slug>", "currentPhase": "plan" }
```

---

## 규칙

- 각 단계는 순서대로 실행 (건너뛰기 금지)
- 이전 단계 산출물이 다음 단계의 입력
- `.bkit` 상태 업데이트는 문서/코드 작성 완료 후 수행
- 모든 요구사항은 명시적이고 테스트 가능해야 함
- 컨벤션은 `docs/conventions/CONVENTIONS.md` 준수
