---
name: require-verification-before-stop
enabled: true
event: stop
action: warn
pattern: .*
---

**종료 전 검증 확인**

작업을 마치기 전에 다음을 확인한다.

1. 변경한 동작을 검증하는 가장 좁은 테스트를 실행했는가?
2. 코드 변경이 있으면 `git diff --check`를 실행했는가?
3. 외부 계약·모듈 경계를 바꿨다면 관련 Mermaid 문서가 실제 구현과 일치하는가?
4. 실행하지 못한 검증은 이유와 함께 사용자에게 알렸는가?
