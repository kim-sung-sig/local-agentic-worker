---
name: "sdd:tests"
description: "SDD와 스켈레톤에서 TDD 테스트 코드를 생성합니다.
  '테스트 작성', 'TDD', '단위 테스트', '테스트 코드 생성', 'JUnit 테스트',
  'write tests', 'TDD', 'unit tests', 'test cases', 'generate tests' 등의 요청에 반응합니다."
---

Generate TDD test code from an SDD and a code skeleton.

Input: $ARGUMENTS
(Provide the SDD path and skeleton path, e.g. `docs/specs/SDD_approval-system.md apps/chat/chat-server/src/main/java/com/example/chat/approval`.)

## Instructions

1. Read the SDD and skeleton to extract behaviors, invariants, and edge cases.
2. If a planning document exists at `docs/planning/<slug>_plan.md`, use it to expand acceptance criteria.
3. Build a test matrix mapping each SDD requirement to one or more test cases.
4. Implement tests under `apps/chat/chat-server/src/test/java/com/example/chat/<context>/`.
5. Test structure rules:
   - JUnit 5 `@Nested` per public method under test
   - Nested groups: `HappyPath`, `Boundary`, `Failure` as needed
   - `@Mock` + `@InjectMocks` (Mockito) for all collaborators
   - `@DisplayName` in **Korean** for every class, nested class, and method
   - Given / When / Then structure in method bodies
6. Verify captured arguments and interaction counts where behavior is non-trivial.
7. Include boundary value tests for numeric ranges and empty/null inputs.
8. Add API tests only when contract verification is needed; prefer unit tests.
9. Optionally output a test plan at `docs/tests/<slug>_testplan.md`.
10. List missing inputs or open questions explicitly at the end.

## Skill Connection

Output tests → reviewed by `/sdd:review`.
