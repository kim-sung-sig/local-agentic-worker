---
name: "sdd:status"
description: ".bkit 상태를 조회하여 현재 피처의 SDD 진행 상황을 표시합니다.
  '현재 상태', 'SDD 진행률', '피처 상태', '.bkit 상태', '어느 단계',
  'status', 'current phase', 'progress', 'bkit state', 'where are we' 등의 요청에 반응합니다."
---

# sdd:status — .bkit 상태 조회

현재 피처의 SDD/PDCA 진행 상황을 `.bkit` 상태 파일에서 읽어 표시합니다.

Input: $ARGUMENTS
(feature slug 선택사항, e.g. `issue-status-transition` — 없으면 primaryFeature 사용)

---

## Step 1 — .bkit 상태 읽기

다음 파일을 읽습니다:
- `.bkit/state/pdca-status.json` — 피처별 상세 상태
- `.bkit/state/memory.json` — 현재 활성 피처/페이즈

`$ARGUMENTS`가 있으면 해당 slug의 feature 블록 조회.
없으면 `memory.json`의 `currentFeature` 사용.

---

## Step 2 — 상태 출력 형식

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 SDD Status: <slug>
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

현재 페이즈: {phase} (phase {phaseNumber}/5)
진행률:      [{completed}/{total}] ████░░░░ 40%
matchRate:   {N}% ({등급}) | null이면 "미측정"

PDCA 단계별 진행:
  ✅ plan   (1) — docs/01-plan/features/<slug>.plan.md
  ✅ design (2) — docs/02-design/features/<slug>.design.md
  🔄 do     (3) — 진행중
  ⏳ check  (4) — 대기
  ⏳ act    (5) — 대기

산출물:
  📄 Plan:   {documents.plan 또는 "없음"}
  📄 Design: {documents.design 또는 "없음"}
  🗂️ 마지막 파일: {lastFile}

타임스탬프:
  시작:        {timestamps.started}
  마지막 수정: {timestamps.lastUpdated}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
➡️  다음 커맨드: /sdd:{다음 단계}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 페이즈별 다음 커맨드 매핑

| 현재 phase | 다음 커맨드 |
|-----------|------------|
| plan (1)   | `/sdd:design docs/01-plan/features/<slug>.plan.md` |
| design (2) | `/sdd:skeleton docs/02-design/features/<slug>.design.md` |
| do (3)     | `/sdd:tests docs/02-design/...` 또는 `/sdd:review ...` |
| check (4)  | matchRate ≥ 90 → 완료 / 미만 → `/sdd:skeleton` 재실행 |
| act (5)    | 완료 ✅ |

---

## Step 3 — 전체 활성 피처 목록 (slug 미지정 시)

`pdca-status.json`의 `activeFeatures` 배열을 순회하여 요약 표시:

```
활성 피처 목록 ({N}개):
  • <slug-1>  [plan]   — 시작: 2026-03-30
  • <slug-2>  [do]     — matchRate: 87%
  • <slug-3>  [check]  — matchRate: 95% ✅
```
