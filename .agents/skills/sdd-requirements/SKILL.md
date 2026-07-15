---
name: sdd-requirements
description: "Structure raw requirements into the repo SDD template; use when converting specs, planning notes, or user requests into SDD format."
---

# SDD Requirements Skill

## Objective
Convert raw requirements into a complete SDD document using the repo template.

## Inputs
- Raw requirements (text, bullets, or notes).
- Optional: Planning document created from `docs/planning/PLANNING_TEMPLATE.md`.

## Output Path Convention
- Default SDD path: `docs/specs/SDD_<slug>.md`
- Use a short slug derived from the feature name (kebab-case).

## Procedure
- Read `docs/specs/SDD_TEMPLATE.md` and follow its headings exactly.
- If a planning document is provided, map its sections into the SDD where relevant.
- Keep every requirement explicit and testable; avoid vague statements.
- If information is missing, mark the field with `TBD:` and list the missing detail.
- Keep domain language consistent and aligned to `AGENTS.md` principles.
- Output the SDD as Markdown, UTF-8 encoding.

## Output Format
- A single SDD Markdown document following the template structure.
- Include an `Open Questions` section populated with any blocking ambiguities.
- Add `Related Docs` links to the planning, skeleton, and tests if known.

## Skill Connection Flow
- This SDD is the input for `$sdd-read`, `$spec-to-skeleton`, and `$skeleton-to-tests`.
- Use the same `<slug>` to keep documents linked.

## Example Usage
"Use $sdd-requirements to convert the following requirements into SDD. Output to docs/specs/SDD_approval-system.md."
