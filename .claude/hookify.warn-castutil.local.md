---
name: warn-castutil
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: \(String\)\s*\w|\(Integer\)\s*\w|\(Long\)\s*\w|\(int\)\s*\w|\(long\)\s*\w|\(Boolean\)\s*\w
---

**직접 형변환(cast) 감지 — CastUtil 활용 가능**

Java 파일에서 명시적 캐스팅 `(String)`, `(Integer)`, `(Long)` 등을 사용하고 있습니다.

**규칙:** 이 프로젝트에는 `CastUtil`이 있습니다. 직접 캐스팅 대신 CastUtil 메서드를 활용하세요.

**활용 예:**
```java
// 직접 캐스팅 대신
String value = CastUtil.objectToString(obj);

// CastUtil이 제공하는 메서드를 먼저 확인하고 적합한 것을 사용한다
```

CastUtil 클래스 위치를 확인하려면: `Grep("class CastUtil", "*.java")`
