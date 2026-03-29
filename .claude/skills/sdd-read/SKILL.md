---
name: sdd-read
description: "Read an SDD and produce a developer handoff brief; use when delegating work to a developer/subagent with team and framework conventions."
---

# SDD Read Skill

## Objective
Translate an SDD into a developer handoff brief that is decision-complete for implementation.

## Inputs
- Required: SDD document in the repo template.
- Optional: Planning document created from `docs/planning/PLANNING_TEMPLATE.md`.
- Optional: Known team conventions or target module.

## Output Path Convention
- Default handoff brief path: `docs/briefs/<slug>_dev-brief.md`

## Procedure
- Read the SDD and extract: goals, constraints, required outputs, API/interface changes, test expectations.
- If a planning document is provided, merge its goals/non-goals and risks.
- Produce a developer brief with:
  - Goal (1-3 bullets)
  - Scope (paths/modules)
  - Constraints (must/forbidden)
  - Done criteria (explicit checks)
  - Tests (run or skip + reason)
  - Inputs (links to SDD, planning)
- Keep it compatible with `/develop` request format.
- Avoid implementation decisions unless already specified in the SDD.

## Output Format
- Markdown brief at `docs/briefs/<slug>_dev-brief.md`.

## Skill Connection Flow
- Input SDD path: `docs/specs/SDD_<slug>.md`
- Output brief is used to invoke a developer agent or `$spec-to-skeleton`.

## Example Usage
"Use $sdd-read to create a developer brief from docs/specs/SDD_approval-system.md and write docs/briefs/approval-system_dev-brief.md."
