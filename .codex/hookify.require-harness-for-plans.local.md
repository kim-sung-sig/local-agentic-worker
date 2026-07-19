---
name: require-harness-for-plans
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: (^|[\\/])docs[\\/](superpowers[\\/]plans|01-plan|planning)[\\/].*\\.md$
  - field: new_text
    operator: regex_match
    pattern: (?m)^#\s+
  - field: new_text
    operator: not_contains
    pattern: '## 하네스 적용'
---

새 계획 또는 구현계획 문서는 `## 하네스 적용` 섹션을 반드시 포함해야 합니다. `.agents/agent-team.md`를 확인해 작업 성격에 맞는 탐색·구현·리뷰 역할, 적용할 하네스 규칙, 검증 책임을 계획에 명시한 뒤 다시 작성하세요.
