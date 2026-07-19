---
name: block-contract-framework-dependency
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: (?i)[\\/]contracts[\\/].*\.java$
  - field: new_text
    operator: regex_match
    pattern: import\s+(org\.springframework|jakarta\.persistence|io\.temporal)\.
---

**계약 모듈 프레임워크 의존성 차단**

`contracts`는 Control Plane, Java Agent Engine, 향후 Python Activity Worker가 공유하는 메시지 계약이다.

- Spring, JPA, Temporal 어노테이션·타입을 계약에 추가하지 않는다.
- Java 표준 라이브러리와 직렬화 가능한 불변 DTO(record)만 사용한다.
- 기술별 설정과 변환은 각 애플리케이션의 infrastructure adapter에 둔다.
