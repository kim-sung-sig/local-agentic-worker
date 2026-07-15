---
name: orchestrator-entity-mutation
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: Orchestrator\.java$
  - field: new_text
    operator: regex_match
    pattern: \.(rename|changeCategory|reOrder|change[A-Z]\w+|update[A-Z]\w+|set[A-Z]\w+)\(
---

⚠️ **Orchestrator에서 엔티티 직접 변경 감지**

Orchestrator 클래스에서 엔티티 mutation 메서드(`.rename()`, `.changeCategory()` 등)를 직접 호출하려 하고 있습니다.

**책임 분리 원칙:**

| 클래스 | 역할 |
|---|---|
| **Orchestrator** | 흐름 조합 — resolve → 조회 → Writer 위임 |
| **Writer** | 불변식 보호 — 검증 + 엔티티 변경 + 저장 |

**Orchestrator가 해야 할 것:**
```java
// ✅ 올바른 패턴
ServiceZone zone = serviceZoneWriter.findOrThrow(id);   // 조회
serviceZoneWriter.updateServiceZone(zone, command);      // Writer에 위임
```

**Writer가 해야 할 것:**
```java
// ✅ 올바른 패턴 (Writer)
public void updateServiceZone(ServiceZone zone, ServiceZoneUpdateCommand command) {
    validateCategory(command.category());  // 검증도 Writer 책임
    zone.rename(command.name());
    zone.changeCategory(command.category());
    zone.reOrder(command.sortOrder());
}
```

**엔티티 변경 메서드 호출은 반드시 Writer(Component) 계층으로 이동하세요.**
