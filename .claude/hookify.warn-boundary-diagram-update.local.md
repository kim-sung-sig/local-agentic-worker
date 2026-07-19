---
name: warn-boundary-diagram-update
enabled: true
event: file
action: warn
conditions:
  - field: file_path
    operator: regex_match
    pattern: (?i)[\\/](domain[\\/]model|application[\\/]port)[\\/].*\.java$
  - field: new_text
    operator: regex_match
    pattern: \b(class|interface|record|enum)\s+\w+
---

**도메인·Port 구조 변경 감지**

공개 도메인 모델 또는 Port 인터페이스가 바뀌면 다음을 확인한다.

1. `docs/architecture/`의 Mermaid 구조 다이어그램이 현재 경계를 설명하는가?
2. 앱 간 공유가 필요하면 구현체가 아니라 `contracts` DTO로만 연결했는가?
3. 새 메서드의 입력이 3개 이상이거나 같은 타입이 연속되면 Command record로 묶을 수 있는가?

단순 private 구현 변경에는 다이어그램을 추가하지 않는다.
