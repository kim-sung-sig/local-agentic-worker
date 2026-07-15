---
name: java-method-ambiguous-params
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: (public|private|protected)\s+[\w<>\[\]]+\s+\w+\s*\([^)]*\b(\w+)\s+\w+\s*,\s*\2\s+\w+[^)]*\)
---

🚫 **Java 메서드 파라미터 타입 연속 중복 감지**

같은 타입의 파라미터가 연속으로 나열된 메서드 시그니처입니다 (예: `String name, String category`, `int x, int y`).

**이 패턴이 나쁜 이유:**
- 호출부에서 인자 순서를 착각해도 컴파일 에러가 나지 않는다.
- 특히 `String, String`처럼 흔한 타입 연속은 실수 위험이 크다.

**대신 이렇게 하세요:**

```java
// ❌ 나쁨 — 순서 착각 위험
public ServiceZone createServiceZone(ServiceGroup service, String name, String category, int sortOrder)

// ✅ 좋음 — Command 객체로 묶기
public ServiceZone createServiceZone(ServiceGroup service, ServiceZoneCreateCommand command)

public record ServiceZoneCreateCommand(String name, String category, int sortOrder) {}
```

**참고:** 타입이 서로 다른 파라미터가 3개 이상인 경우는 `java-method-long-signature` 규칙(warn)이 별도로 안내합니다. 의미 있는 신규 파라미터 추가라면 그대로 진행해도 됩니다.

**Command 위치:** `service/command/orchestrator/request/` 또는 `service/command/component/`
