---
name: sdd-review
description: "Review SDD, skeleton, and tests for convention compliance and spec alignment; use for architecture and test coverage checks."
---

# SDD Review Skill

## Objective
Review the SDD, generated skeleton, and tests for correctness, convention compliance, and coverage.

## Inputs
- Required: SDD document in the repo template.
- Required: Skeleton code generated from the SDD.
- Required: Tests generated from the skeleton.
- Optional: Planning document created from `docs/planning/PLANNING_TEMPLATE.md`.

## Review Checklist
- SDD completeness:
  - All required sections filled or explicitly `TBD`
  - Open Questions populated for ambiguities
- Skeleton compliance:
  - DDD boundaries and package structure match SDD
  - Spring Boot and team conventions followed
  - No business logic in skeleton
- Test coverage:
  - Each SDD requirement maps to at least one test
  - Domain/service tests prioritized
  - API tests cover contracts
- Consistency:
  - Naming across SDD, skeleton, and tests is aligned
  - Error codes and validation rules are reflected

## Output Format
- Review report in Markdown with:
  - Findings (severity, location)
  - Missing coverage list
  - Recommendations

## Example Usage
"Use $sdd-review to review docs/specs/SDD_approval-system.md, apps/chat/chat-server/src/main/java/com/example/chat/approval, and apps/chat/chat-server/src/test/java/com/example/chat/approval."
