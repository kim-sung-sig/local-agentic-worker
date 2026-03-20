---
name: explain
description: "코드나 설계를 이해하기 쉽게 설명합니다.
  '설명해줘', '이게 뭐야', '어떻게 동작해', '왜 이렇게 설계', '코드 분석', '이해가 안 돼',
  'explain', 'what does this do', 'how does this work', 'why is this designed' 등의 요청에 반응합니다."
---

Explain the code or design in the given scope.

Scope: $ARGUMENTS

## Instructions

1. Read the relevant files in the specified scope.
2. Identify the bounded context, layer, and purpose of each component.
3. Explain:
   - **What it does**: high-level purpose
   - **How it works**: key flow, data transformations, dependencies
   - **Why it's designed this way**: reference to DDD/CQRS patterns or constraints from `docs/conventions/CONVENTIONS.md`
4. If the scope includes an SDD, cross-reference the implementation against the spec.
5. Highlight any non-obvious decisions or known technical debt.
