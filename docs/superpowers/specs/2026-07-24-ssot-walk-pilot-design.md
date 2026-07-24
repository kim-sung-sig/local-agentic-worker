# SSoT / Walk 파일럿 설계 — control-plane

- 작성일: 2026-07-24
- 상태: 승인됨 (브레인스토밍 완료)
- 범위: `apps/control-plane` 앱 전체 (파일럿)

## 1. 배경 & 목표

**단일 공급 진실원(SSoT)** 체계를 이 저장소에 파일럿으로 도입한다.

- `ssot/` = "현재 이 도메인이 할 수 있는 것"을 코드 기준으로 정확히 기술하는 **진실원**.
- `walk/` = 도메인별 기능 개발이 실제로 진행되는 **작업 공간**(plan → design → 구현 → report).
- `DOMAIN.md` = 도메인과 그 ssot·walk 경로를 가리키는 **레지스트리**.

멀티플랫폼(Claude Code + Codex) 양쪽에서 동작해야 한다.

## 2. 결정 사항 (브레인스토밍 합의)

| # | 결정 | 선택 |
|---|------|------|
| 1 | 구축 범위 | control-plane 앱 전체 파일럿 |
| 2 | 위치 | 앱 내부 `docs/` + 루트 `docs/DOMAIN.md` 인덱스 |
| 3 | ssot 촘도 | `issue`·`project`는 코드 기반 실촘도, 나머지는 템플릿 |
| 4 | 멀티플랫폼 | 공유 규약 + 공유 스크립트 + 양쪽 얕은 래퍼 |
| 5 | 공유 대상 | **SSoT 관련 자산만**. 기존 skill·rule·hook은 범위 밖 |

## 3. 디렉토리 레이아웃

```
docs/DOMAIN.md                          # [루트 인덱스] app → domains → app DOMAIN.md 경로
docs/conventions/SSOT.md                # [공유 규약] 양 플랫폼이 참조하는 유일한 규약
scripts/ssot/walk-new.mjs               # [공유 스크립트] 무의존 Node, walk feature 스캐폴드

apps/control-plane/docs/
  DOMAIN.md                             # [앱 레지스트리] domain/subdomain → ssot·walk 경로 + status
  ssot/
    _TEMPLATE.md                        # 도메인 스펙 템플릿
    issue/SPEC.md                       # 실촘도 (seeded)
    project/SPEC.md                     # 실촘도 (seeded)
    auth/SPEC.md                        # 템플릿
    document/SPEC.md                    # 템플릿
    document/revision/SPEC.md           # 서브도메인 예시 (중첩 실증)
    notification/SPEC.md                # 템플릿
  walk/
    _TEMPLATE/
      plan.md  design.md  _workspace/.gitkeep  report/.gitkeep  reference/.gitkeep
    issue/feature/.gitkeep
    project/feature/.gitkeep
    auth/feature/.gitkeep
    document/feature/.gitkeep
    notification/feature/.gitkeep
```

- **ssot = 도메인당 폴더 + `SPEC.md`** → 폴더 중첩으로 서브도메인 표현(`document/revision/SPEC.md`).
- **walk = `walk/{domain}/feature/{slug}/{plan.md, design.md, _workspace/, report/, reference/}`**.

## 4. ssot 도메인 산출물 (4종 파일)

한 도메인의 진실원은 `ssot/{domain}/` 아래 4개 파일로 구성한다 (규약 `docs/conventions/SSOT.md` §2):
- `SPEC.md` — 능력/계약 (Purpose/Capabilities/API surface/Data model/Invariants/Dependencies/Out of scope/Source refs)
- `CONTEXT.md` — 바운디드 컨텍스트 (경계, 컨텍스트 맵, 통합 지점, published language)
- `MODEL.md` — 도메인 모델 (Aggregate/Entity/VO/도메인 이벤트/영속 매핑)
- `LANGUAGE.md` — 유비쿼터스 언어 (용어→정의→코드 식별자)

중복 금지: 같은 사실은 한 파일에만 (예: 이벤트 스키마=MODEL, 발행 능력=SPEC, 구독자=CONTEXT).

`issue`·`project`는 4종 모두 아래 코드에서 추출:
- `apps/control-plane/server/utils/issue-service.ts`, `project-service.ts`
- `apps/control-plane/server/api/**`

정확도 포인트: `issues/[issueId]/status.patch.ts`는 `z.string().min(1)`만 검증 → **상태 전이가 서버에서 강제되지 않음**을 Out of scope에 명시.

## 5. DOMAIN.md 2종

- 앱 레지스트리: 표 `domain | subdomains | ssot 경로 | walk 경로 | status`.
- 루트 인덱스: 표 `app | domains | app DOMAIN.md 링크`.

## 6. 멀티플랫폼 운영 계층

핵심 원칙: **실질 내용은 플랫폼-중립 파일에 한 번, `.claude`/`.codex`에는 얇은 래퍼만.**

- 두 CLI는 서로의 폴더를 읽지 않음 → Claude=`​.claude/`, Codex=`.codex/`.
- SSoT 실질(`docs/**`, `scripts/**`)은 하네스 폴더 밖 → 진짜 단일본.
- 래퍼: `.claude/commands/ssot/SKILL.md`, `.codex/prompts/ssot.md` — 각 3~4줄, `SSOT.md` 참조 + 스크립트 호출.
- 래퍼는 슬래시 커맨드/프롬프트라 `model` 값이 불필요 → agent에서 발생하는 model-divergence 문제가 없음.

## 7. 최소 변경

- `docs/conventions/CONVENTIONS.md`에 `SSOT.md` 포인터 1줄.
- 기존 코드/설정/agent/hook 변경 없음.

## 8. 범위 밖 (YAGNI)

- walk→ssot 자동 승격 스크립트
- ssot ↔ 코드 동기화 검증 훅
- 나머지 3개 앱(worker-gateway, temporal-worker, python-agent-worker)
- SSoT 전용 agent (필요 시 model 프론트매터만 플랫폼별로 분리)

## 9. 검증

`node scripts/ssot/walk-new.mjs control-plane issue sample-feature` 실행 → feature 디렉토리 생성 확인 후 샘플 제거.
