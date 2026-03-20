---
name: sdd
description: "SDD(Software Design Document) 스킬체인의 진입점입니다. requirements → read → skeleton → tests → review 순서로 진행합니다.
  'SDD', '설계 문서 작성', '소프트웨어 설계', '스펙 작성', '설계 체인',
  'software design document', 'SDD chain', 'spec', 'design doc' 등의 요청에 반응합니다."
---

SDD (Software Design Document) 스킬체인 개요

$ARGUMENTS 없이 호출 시 이 개요를 표시합니다.

## SDD 스킬체인

```
/sdd:requirements  →  /sdd:read  →  /sdd:skeleton  →  /sdd:tests  →  /sdd:review
```

| 커맨드 | 역할 | 입력 | 출력 |
|--------|------|------|------|
| `/sdd:requirements` | 요구사항 → SDD 문서 | 요구사항 텍스트 | `docs/specs/SDD_<slug>.md` |
| `/sdd:read` | SDD → 개발자 브리프 | SDD 경로 | `docs/briefs/<slug>_dev-brief.md` |
| `/sdd:skeleton` | SDD → 코드 스켈레톤 | SDD 경로 | 패키지 stub 파일들 |
| `/sdd:tests` | SDD + 스켈레톤 → TDD 테스트 | SDD + 스켈레톤 경로 | 테스트 파일들 |
| `/sdd:review` | SDD + 스켈레톤 + 테스트 리뷰 | 세 경로 모두 | 리뷰 리포트 |

## 빠른 시작

```bash
# 1. 요구사항으로 SDD 작성
/sdd:requirements "채널 멤버 승인 시스템 - 채널장이 가입 요청을 승인/거부할 수 있어야 한다"

# 2. 개발자 브리프 생성
/sdd:read docs/specs/SDD_approval-system.md

# 3. 코드 스켈레톤 생성
/sdd:skeleton docs/specs/SDD_approval-system.md

# 4. TDD 테스트 생성
/sdd:tests docs/specs/SDD_approval-system.md apps/chat/chat-server/src/main/java/com/example/chat/approval

# 5. 리뷰
/sdd:review docs/specs/SDD_approval-system.md apps/chat/chat-server/src/main/java/com/example/chat/approval apps/chat/chat-server/src/test/java/com/example/chat/approval
```

## 규칙

- 각 단계는 순서대로 실행 (건너뛰기 금지)
- 이전 단계 출력이 다음 단계의 입력
- SDD는 `docs/specs/SDD_TEMPLATE.md` 구조 준수
- 모든 요구사항은 명시적이고 테스트 가능해야 함
