---
name: require-swagger-operation
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: src[\/].*main[\/]java[\/].*(api|controller)[\/].*\.java$
  - field: new_text
    operator: regex_match
    pattern: '@(Get|Post|Put|Patch|Delete)Mapping'
  - field: new_text
    operator: not_contains
    pattern: '@Operation'
---
⚠️ API 엔드포인트를 추가하거나 변경했습니다. Swagger 문서화를 위해 `@Operation(summary = "...")`을 함께 작성하세요.
