---
name: spec-to-skeleton
description: "Design DDD and Spring Boot code skeletons from SDD specs; use when creating package structure, interfaces, and stubs without implementation."
---

# Spec To Skeleton Skill

## Objective
Generate a code skeleton from an SDD, aligned to DDD and project conventions.

## Inputs
- Required: SDD document in the repo template.
- Optional: Existing modules or code hints in the repo.

## Output Path Convention
- Default code root: `apps/chat/chat-server/src/main/java/com/example/chat/<context>`
- Default skeleton notes: `docs/design/skeletons/<slug>/README.md`

## Procedure
- Read `AGENTS.md` for project rules.
- Read the SDD template at `docs/specs/SDD_TEMPLATE.md` and apply its sections.
- Use `$spring-boot-skill` for Spring Boot conventions and layering.
- Decompose by bounded context and aggregates; map to packages and modules.
- Create minimal, compilable stubs for every SDD section that implies code:
  - Interfaces (controllers, requests/responses)
  - Domain model (entities, value objects, status enums)
  - Application services (commands/queries)
  - Repositories / ports
  - Error codes / exceptions if specified
- Do not implement business logic; keep methods minimal and deterministic.
- Avoid magic strings/numbers; introduce constants or enums where necessary.

## Output Format
- Code skeleton files under `src/main/java/...`.
- Optional skeleton notes in Markdown.
- List any open questions or missing spec details.

## Skill Connection Flow
- Input SDD path: `docs/specs/SDD_<slug>.md`
- Output skeleton path: `apps/chat/chat-server/src/main/java/com/example/chat/<context>`
- This skeleton is the input for `$skeleton-to-tests` and `$sdd-review`.

## Example Usage
"Use $spec-to-skeleton to generate stubs from docs/specs/SDD_approval-system.md under apps/chat/chat-server/src/main/java/com/example/chat/approval."
