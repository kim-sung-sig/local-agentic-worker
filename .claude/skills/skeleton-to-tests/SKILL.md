---
name: skeleton-to-tests
description: "Generate TDD tests from an SDD and code skeleton; use when drafting unit and API tests based on specifications and stubs."
---

# Skeleton To Tests Skill

## Objective
Create TDD test code from the SDD and the generated skeleton.

## Inputs
- Required: SDD document in the repo template.
- Required: Code skeleton (classes/interfaces/stubs).
- Optional: Planning document created from `docs/planning/PLANNING_TEMPLATE.md` to enrich scenarios.

## Output Path Convention
- Default test root: `apps/chat/chat-server/src/test/java/com/example/chat/<context>`
- Optional test plan path: `docs/tests/<slug>_testplan.md`

## Procedure
- Read the SDD and skeleton to extract behaviors and invariants.
- If a planning document is provided, use it to expand edge cases and acceptance criteria.
- Build a test matrix mapping requirements to test cases.
- Implement mock-based unit tests as the default:
  - Mock repositories and external dependencies (Mockito)
  - Keep domain tests small and focused on invariants
- Include boundary value tests for numeric ranges and empty inputs.
- Add API tests only when necessary; prefer unit tests for most cases.
- Confirm the test framework from build files before choosing libraries or annotations.

## Test Structure Rules
- Use JUnit5 `@Nested` by method:
  - One nested class per public method under test
  - Inside each nested class, create `HappyPath`, `Boundary`, and `Failure` nested groups as needed
- Use Mockito for all collaborators:
  - Prefer `@Mock` and `@InjectMocks`
  - Verify interactions and captured arguments
- Use `@DisplayName` in Korean for each test and nested group
- Use Given/When/Then naming in test methods and comments

## Output Format
- Test classes and methods under `src/test/java/...`.
- Optional Markdown test plan.
- List missing inputs or open questions explicitly.

## Skill Connection Flow
- Input SDD path: `docs/specs/SDD_<slug>.md`
- Input skeleton path: `apps/chat/chat-server/src/main/java/com/example/chat/<context>`
- Output tests path: `apps/chat/chat-server/src/test/java/com/example/chat/<context>`
- Output is reviewed by `$sdd-review`.

## Example Usage
"Use $skeleton-to-tests to write tests for apps/chat/chat-server/src/main/java/com/example/chat/approval using docs/specs/SDD_approval-system.md."
