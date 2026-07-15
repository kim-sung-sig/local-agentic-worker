---
name: warn-maven-tail
enabled: true
event: bash
action: block
conditions:
  - field: command
    operator: regex_match
    pattern: mvnw.*(compile|test|package|install|verify).*\|\s*tail
---

**Maven 출력 tail 방식 감지**

`./mvnw compile ... | tail -N` 방식으로 컴파일 결과를 읽으려 하고 있습니다.

**문제:** `tail -N`은 오류가 스크롤 밖으로 잘릴 수 있고 노이즈가 많아 컨텍스트 낭비입니다.

**올바른 방식:** `[ERROR]`/`[WARN]` 행만 필터링한다.

```bash
./mvnw compile 2>&1 | grep -E "^\[ERROR\]|^\[WARN\]" || true
```

- 오류 없으면 빈 출력 → 컨텍스트 0 소비
- `|| true`: grep 매칭 없을 때 exit 1 방지
