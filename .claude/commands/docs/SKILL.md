---
name: docs
description: "문서를 작성하거나 최신 상태로 업데이트합니다.
  '문서 작성', '문서화', 'README 업데이트', 'API 문서', 'SDD 업데이트', '주석 추가',
  'docs', 'document', 'README', 'API docs', 'update spec', 'write documentation' 등의 요청에 반응합니다."
---

Update or draft documentation for the specified scope.

Scope: $ARGUMENTS

## Instructions

1. Read the relevant source files and any existing docs for the scope.
2. Determine what documentation is needed:
   - **SDD update**: if the implementation diverged from `docs/specs/SDD_<slug>.md`, update the spec to match reality.
   - **Context README**: if `summary.md` or `README.md` is missing or outdated for a bounded context, create/update it.
   - **API docs**: verify SpringDoc annotations (`@Operation`, `@ApiResponse`) are accurate and complete.
   - **Architecture decision**: if a notable design decision was made, document it with context and trade-offs.
3. Keep language consistent with the existing bounded context terminology.
4. All output must be UTF-8 Markdown.
5. Do not invent behavior — document only what the code actually does.
