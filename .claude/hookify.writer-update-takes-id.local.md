---
name: writer-update-takes-id
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: Writer\.java$
  - field: new_text
    operator: regex_match
    pattern: (public|private)\s+\w[\w<>]*\s+update\w*\s*\(\s*(Long|long)\s+id
---

⚠️ **Writer update 메서드가 id(Long)를 받고 있습니다**

Writer의 update 메서드는 `Long id`가 아닌 **이미 조회된 엔티티**를 받아야 합니다.

**이유:**
- `id`로 받으면 Writer가 내부에서 조회까지 하게 됨 → 오케스트레이터가 조회 결과를 활용할 수 없음
- 오케스트레이터에서 조회 후 추가 검증/조합 로직을 끼워 넣을 수 없음
- 패턴: **Orchestrator가 findOrThrow → entity를 Writer에 전달**

**올바른 패턴:**

```java
// ❌ 나쁨 (Writer가 id로 조회까지 함)
public void updateServiceZone(Long id, String name, String category, int sortOrder) {
    ServiceZone zone = findOrThrow(id);
    ...
}

// ✅ 좋음 (entity를 받아서 변경만 담당)
public void updateServiceZone(ServiceZone zone, ServiceZoneUpdateCommand command) {
    validateCategory(command.category());
    zone.rename(command.name());
    zone.changeCategory(command.category());
    zone.reOrder(command.sortOrder());
}
```

**Orchestrator에서:**
```java
ServiceZone zone = serviceZoneWriter.findOrThrow(id);  // 조회
// 여기서 zone 기반 추가 검증 가능
serviceZoneWriter.updateServiceZone(zone, command);    // 변경 위임
```
