---
name: warn-method-chain-depth
enabled: true
event: file
action: warn
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: \.\w+\([^()]*\)\s*\.\w+\(
  - field: new_text
    operator: regex_match
    pattern: (?is)^(?!.*\b(?:stream|optional|builder|webclient|mono|flux|filter|flatmap|collect|reduce|sorted|distinct|limit|skip|foreach|anymatch|allmatch|nonematch|findfirst|findany|tolist|toset|toarray|orelse|orelseget|orelsethrow|ifpresent|ifpresentorelse|ispresent|isempty|build|tobuilder|uri|retrieve|bodytomono|bodytoflux|exchangetomono|exchangetoflux|subscribe|block|doonnext|doonerror|doonsuccess|onerrorresume|switchifempty)\b).*$
---

**메서드 체이닝 2단계 이상 감지**

`a.methodA().methodB()...` 형태로 메서드 체이닝이 2단계 이상 이어지고 있습니다.

**예외 판단 기준:** 특정 메서드 이름을 화이트리스트로 두는 것이 아니라, `a` 자체가 애초에 체이닝으로 설계된 시스템인지로 판단한다. `Stream`, `Optional`, `Builder`, `WebClient`(Reactor `Mono`/`Flux` 포함) 위에서의 체이닝은 이 규칙의 대상이 아니다.

**스스로 확인할 기준:**
1. 정말 2단계 이상 체이닝이 필요한가? 중간 결과를 변수로 뽑아 1단계로 줄일 수 있지 않은가?
2. 체이닝 중간 어딘가에서 null이 나올 수 있는가? 그렇다면 그 지점을 변수로 분리해 즉시 확인해야 하지 않는가?
3. 이 지점에 나중에 로깅이나 추가 처리가 붙을 가능성이 있는가? 있다면 미리 변수로 분리해 두는 게 맞지 않는가?
4. `a`가 `Stream`/`Optional`/`Builder`/`WebClient`처럼 원래 체이닝 전용으로 설계된 대상인가? 맞다면 그대로 둔다.

**원칙:**
- 체이닝은 1단계를 우선한다.
- 2단계는 위 기준을 검토해 필요하다고 판단될 때만 허용한다.
- 3단계 이상은 지양하고, 각 단계를 지역 변수로 분리한다.
- 이유: 나중에 로깅 추가나 중간 단계 수정이 필요할 때 코드 수정 범위를 최소화하고, 코드 자체의 간결성을 유지하기 위함이다.
