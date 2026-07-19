---
name: require-request-validation
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: src[\\/].*main[\\/]java[\\/].*(api|controller)[\\/].*\\.java$
  - field: new_text
    operator: contains
    pattern: '@RequestBody'
  - field: new_text
    operator: not_contains
    pattern: '@Valid'
---
⚠️ `@RequestBody` 요청을 추가하거나 변경했습니다. 요청 DTO 검증이 필수이므로 파라미터에 `@Valid`를 함께 적용하세요.
