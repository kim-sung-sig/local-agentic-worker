---
name: warn-pdca-analysis-path
enabled: true
event: prompt
conditions:
  - field: user_prompt
    operator: regex_match
    pattern: /pdca\s+anal|pdca\s+anal
---

**pdca analysis 경로 규칙**

`pdca analysis {feature}` 명령이 감지되었습니다.

**규칙:** 분석 산출물은 반드시 아래 경로에 작성한다.

```
docs/03-analysis/feature/{feature}.analysis.md
```

- `{feature}` = 명령 인자 그대로 사용 (예: `feature-action-comment-improve`)
- 경로가 없으면 디렉토리를 먼저 생성한다
- 기존 파일이 있으면 append-only로 최신 분석을 추가한다
- 분석 시작 전 `./mvnw compile 2>&1 | grep -E "^\[ERROR\]|^\[WARN\]" || true` 로 컴파일 오류를 먼저 확인한다
