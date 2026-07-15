---
name: plan-ask-before-deciding
enabled: true
event: prompt
conditions:
  - field: user_prompt
    operator: regex_match
    pattern: /(pdca\s+plan|plan)|plan\s*mode
action: warn
---

계획 단계 진입이 감지되었습니다.

**규칙: 혼자 판단하지 말고 반드시 먼저 물어보세요.**

여러 구현 방안이 존재할 경우:
1. 각 방안을 보기(선택지)로 제시한다.
2. 추천 방안에 `★ [추천]` 마킹을 붙인다.
3. 사용자가 선택한 뒤에 계획을 작성한다.

선택지 없이 혼자 방향을 결정하고 계획서를 작성하는 것은 금지입니다.
