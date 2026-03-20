---
name: "sdd:read"
description: "SDD를 개발자 구현 브리프로 변환합니다.
  'SDD 읽기', '스펙 파악', '설계 이해', '개발 브리프', '구현 준비',
  'read SDD', 'understand spec', 'developer brief', 'load design', 'parse spec' 등의 요청에 반응합니다."
---

Translate an SDD into a developer handoff brief that is decision-complete for implementation.

Input: $ARGUMENTS
(Provide the SDD path, e.g. `docs/specs/SDD_approval-system.md`, and optionally the planning doc path.)

## Instructions

1. Read the SDD and extract: goals, constraints, required outputs, API/interface changes, test expectations.
2. If a planning document is provided, merge its goals/non-goals and risks.
3. Produce a developer brief at `docs/briefs/<slug>_dev-brief.md` with:
   - **Goal** (1–3 bullets)
   - **Scope** (paths/modules to touch)
   - **Constraints** (must/forbidden)
   - **Done Criteria** (explicit checks)
   - **Tests** (run or skip + reason)
   - **Inputs** (links to SDD and planning doc)
4. Do not make implementation decisions unless already specified in the SDD.
5. The brief format must be compatible with `/pdca do` implementation phase.

## Skill Connection

Output brief → used as input for `/sdd:skeleton` or `/pdca do`.
