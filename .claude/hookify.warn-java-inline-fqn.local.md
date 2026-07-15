---
name: warn-java-inline-fqn
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: new\s+java\.(util|lang|io|nio|math)\.\w+|java\.(util|lang)\.\w+\.(of|copyOf|valueOf|asList|format|empty|singleton|stream)\(
---

**Java 인라인 전체 경로(FQN) 감지**

코드 본문에 `java.util.Map`, `java.util.List` 등 전체 경로를 직접 쓰고 있습니다.

**금지 패턴 예:**
- `java.util.Map.of(...)`
- `new java.util.HashMap<>()`
- `java.util.List.of(...)`

**올바른 방식:** 파일 상단 `import` 구문으로 선언하고 단순 클래스명으로 사용한다.

```java
import java.util.Map;
import java.util.List;

// 코드에서는
Map.of(...)
List.of(...)
```
