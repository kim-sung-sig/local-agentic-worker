---
name: java-method-long-signature
enabled: true
event: file
action: warn
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: (public|private|protected)\s+[\w<>\[\]]+\s+\w+\s*\([^)]+,[^)]+,[^)]+\)
---

⚠️ **Java 메서드 파라미터 3개 이상 감지** (참고용 경고 — 의미 있는 파라미터라면 그대로 진행)

파라미터가 3개 이상인 메서드 시그니처를 작성하려 하고 있습니다.

**이 패턴이 나쁜 이유:**
- 파라미터 순서 실수 가능성 증가 (String, String, int → 어느 게 어느 건지?)
- 호출부 가독성 저하
- 나중에 파라미터 추가 시 모든 호출부 수정 필요

**대신 이렇게 하세요:**

```java
// ❌ 나쁨
public ServiceZone createServiceZone(ServiceGroup service, String name, String category, int sortOrder)

// ✅ 좋음 — Command 객체로 묶기
public ServiceZone createServiceZone(ServiceGroup service, ServiceZoneCreateCommand command)

// Command 객체
public record ServiceZoneCreateCommand(String name, String category, int sortOrder) {}
```

**언제 묶어야 하나:**
- 같은 도메인에서 온 파라미터 2개 이상 → 무조건 Command/DTO
- String, String 연속 → 혼동 위험, 반드시 묶기
- 나중에 파라미터가 추가될 가능성이 있는 경우

**Command 위치:** `service/command/orchestrator/request/` 또는 `service/command/component/`
