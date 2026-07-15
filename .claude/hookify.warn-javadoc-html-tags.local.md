---
name: warn-javadoc-html-tags
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: \*\s*<(ul|li|p|br|ol|div|span|b|i|strong|em|code)\b
---

**Javadoc HTML 태그 감지**

Javadoc 주석 안에 HTML 태그(`<ul>`, `<li>`, `<p>`, `<br>` 등)를 사용하고 있습니다.

**규칙:**
- HTML 태그 사용 금지
- 최대 3줄 이내로 작성
- 목적·핵심 입출력만 간결하게 기술

**잘못된 예:**
```java
/**
 * 결과를 반환한다.
 * <ul>
 *   <li>항목 1</li>
 * </ul>
 */
```

**올바른 예:**
```java
/** 조건에 맞는 진단 목록을 반환한다. */
```
