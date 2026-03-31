---
name: "sdd:read"
description: "[Deprecated] sdd:design으로 대체되었습니다. Plan 문서를 Design 문서로 변환하려면 /sdd:design을 사용하세요."
---

# ⚠️ sdd:read — Deprecated

이 커맨드는 `/sdd:design`으로 대체되었습니다.

## 마이그레이션

| 이전 | 현재 |
|------|------|
| `/sdd:read docs/specs/SDD_<slug>.md` | `/sdd:design docs/01-plan/features/<slug>.plan.md` |

## 이유

SDD 스킬체인이 `.bkit PDCA` 플로우와 통합되면서 단계가 명확히 분리되었습니다:
- Plan 문서 작성: `/sdd:requirements`
- Design 문서 작성: `/sdd:design` ← 이 단계

## 즉시 실행

`$ARGUMENTS`가 있으면 `/sdd:design`으로 자동 위임합니다:

Input: $ARGUMENTS

위의 경로를 받아 `/sdd:design`의 절차를 따라 실행하세요.
