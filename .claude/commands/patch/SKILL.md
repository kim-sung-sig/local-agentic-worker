---
name: patch
description: "범위를 최소화한 집중적인 변경을 구현합니다.
  '수정해줘', '버그 고쳐줘', '이것만 바꿔줘', '추가해줘', '간단히 구현', '딱 이것만',
  'patch', 'fix', 'implement', 'change only this', 'minimal change' 등의 요청에 반응합니다."
---

Implement a focused, minimal change.

Task: $ARGUMENTS

## Instructions

1. Read `docs/conventions/CONVENTIONS.md` before making changes.
2. Change only what is described in the task — do not refactor surrounding code.
3. Identify the exact files to modify; prefer editing over creating new files.
4. After the change, run the relevant module tests to verify nothing is broken:
   `./gradlew :<module>:test --tests "<TestClass>"`
5. If the change touches a domain model or application service, add or update the corresponding unit test.
6. Summarize what was changed and why.

## Policy

- Scope is strictly limited to the stated task
- No refactoring of surrounding code
- No new files unless absolutely required
- Tests must pass before marking done
