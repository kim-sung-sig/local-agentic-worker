---
name: skill-mgr
description: >
  Manage the skill lifecycle: find, import, customize, register, and sync skills.
  Use this skill:
    * When searching for external skills to import (find-skills / skills.sh)
    * When adding a new skill to the project harness
    * When customizing an imported skill for project conventions
    * When syncing .claude/skills/ changes to the registry
    * When reviewing the current skill inventory
---

# Skill Manager Skill

스킬 생태계(vercel-labs/skills, skills.sh, anthropics/skills)에서 스킬을 탐색·도입·개선하고
`.claude/skills/` 관리 체계 안에서 점진적으로 강화합니다.

---

## 스킬 생명주기

```
FIND      → 외부 생태계에서 후보 스킬 탐색
EVALUATE  → 프로젝트 적합성 평가
IMPORT    → .claude/skills/<id>/SKILL.md 로 가져오기
REGISTER  → .claude/.harness/registries/skills.json 등록
SYNC      → scripts/sync-skills.ps1 실행 → .claude/skills/ 갱신
CUSTOMIZE → 프로젝트 컨벤션에 맞게 SKILL.md 수정
VERIFY    → done-gate 통과 확인
ITERATE   → 사용하면서 점진적 개선
```

---

## Phase 1: FIND — 스킬 탐색

### find-skills 메타스킬 사용 (설치 후)

```bash
# find-skills 설치
npx skills add vercel-labs/skills

# 탐색 (interactive)
npx skills find "spring boot"
npx skills find "java testing"
npx skills find "DDD architecture"

# 인기 스킬 목록
npx skills list --registry
```

### 주요 소스

| 소스 | 명령 | 특징 |
|------|------|------|
| vercel-labs/skills | `npx skills add vercel-labs/skills` | find-skills 메타스킬 포함 |
| vercel-labs/agent-skills | `npx skills add vercel-labs/agent-skills` | React, Web 특화 |
| anthropics/skills | `npx skills add anthropics/skills` | 공식 Anthropic |
| skills.sh 마켓 | `npx skills find <query>` | 커뮤니티 500K+ 스킬 |
| skillsmp.com | 웹 검색 | Spring Boot, Java 특화 목록 |

### 품질 필터 기준

- 주간 설치 수 1K+ 또는 GitHub ★ 100+
- MIT/Apache 라이선스
- SKILL.md 프론트매터 규격 준수
- 마지막 업데이트 6개월 이내

---

## Phase 2: EVALUATE — 적합성 평가

가져오기 전 체크:

```
- [ ] 이 스킬이 해결하는 문제가 프로젝트에 실재하는가?
- [ ] 기존 스킬(done-gate, sdd-requirements 등)과 중복되지 않는가?
- [ ] tier 분류: core(불변) vs optional(커스터마이징 가능)?
- [ ] 프로젝트 스택(Spring Boot 3.x, Gradle, Java 21)과 맞는가?
```

---

## Phase 3: IMPORT & REGISTER — 등록

### 파일 생성

```bash
# 1. 스킬 디렉토리 생성
mkdir .claude/skills/<id>

# 2. SKILL.md 작성 또는 복사
# 외부 스킬을 복사할 경우 프로젝트 컨텍스트에 맞게 조정
```

SKILL.md 필수 프론트매터:

```yaml
---
name: <id>          # kebab-case, skills.json id와 동일
description: >
  <언제 이 스킬을 사용하는지 — 구체적 트리거 명시>
---
```

### skills.json 등록

`.claude/.harness/registries/skills.json`의 `managedSkills` 배열에 추가:

```json
{
  "id": "<id>",
  "source": ".claude/skills/<id>/SKILL.md",
  "tier": "optional",
  "mutable": true,
  "tool_aliases": {
    "claude": ["<id>"]
  },
  "adapters": {
    "claude_command_dir": "<id>"
  }
}
```

**tier 선택**:
- `"core"` + `"mutable": false` → 절대 변경 금지 (sdd-requirements 등 파이프라인 핵심)
- `"optional"` + `"mutable": true` → 프로젝트 커스터마이징 가능

### Sync 실행

```bash
pwsh -File scripts/sync-skills.ps1
```

→ `.claude/skills/<id>/SKILL.md` 갱신 (import 스킬만 해당, 직접 편집 금지)

---

## Phase 4: CUSTOMIZE — 프로젝트 특화

외부 스킬을 가져온 후 반드시 확인:

- [ ] 기술 스택 언급이 프로젝트와 맞는가? (Maven → Gradle, Spring Boot 4.x → 3.x)
- [ ] 경로 규칙이 프로젝트 구조와 맞는가? (`apps/chat/chat-server/src/...`)
- [ ] 테스트 규칙이 컨벤션과 맞는가? (`@DisplayName` 한국어 등)
- [ ] 연결되는 스킬 레퍼런스가 올바른가? (`$sdd-requirements`, `$done-gate`)

---

## Phase 5: VERIFY & ITERATE

```bash
# done-gate 실행
pwsh -File scripts/done-gate.ps1

# 스킬 적용 후 피드백:
# - 스킬이 너무 길어 Claude가 일부를 무시? → 섹션 분리
# - 스킬 트리거가 모호? → description을 더 구체적 트리거로 수정
# - 특정 패턴이 반복 오류? → 해당 섹션에 예시 코드 추가
```

---

## 현재 스킬 인벤토리

| ID | tier | mutable | 역할 |
|----|------|---------|------|
| sdd-requirements | core | false | SDD 템플릿 변환 |
| sdd-read | core | false | SDD 조회 |
| spec-to-skeleton | core | false | 코드 뼈대 생성 |
| skeleton-to-tests | core | false | TDD 테스트 생성 |
| sdd-review | core | false | SDD 구현 검토 |
| done-gate | core | false | 완료 게이트 |
| spring-boot | optional | true | Spring Boot 가이드 |
| jpa | optional | true | JPA 패턴 |
| security | optional | true | 보안 검토 |
| refactor | optional | true | 리팩터링 |
| perf | optional | true | 성능 분석 |
| patch | optional | true | 패치 적용 |
| explain | optional | true | 코드 설명 |
| docs | optional | true | 문서화 |
| **sdd-craft** | optional | true | 효과적 SDD 생성 (신규) |
| **tdd-cycle** | optional | true | TDD 개선 사이클 (신규) |
| **arch-policy** | optional | true | 아키텍처 정책 (신규) |
| **skill-mgr** | optional | true | 스킬 관리 (이 스킬) |

---

## Skill Connection

- 스킬 탐색: `npx skills find <query>` (find-skills)
- SDD 기반 스킬 생성: `$sdd-craft` → 스킬 명세 작성 후 SKILL.md 제작
- 완료 검증: `$done-gate`
- bkit 스킬 생성: `/skill-create` (bkit)
