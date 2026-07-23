# SSoT & Walk 규약 (단일 공급 진실원)

> 이 문서는 Claude Code / Codex 양 플랫폼이 참조하는 **유일한** SSoT 운영 규약이다.
> 플랫폼별 래퍼(`.claude/commands/ssot/`, `.codex/prompts/ssot.md`)는 이 문서를 가리킬 뿐 내용을 복제하지 않는다.

## 1. 개념

| 구분 | 위치 | 역할 |
|------|------|------|
| **ssot** | `apps/{app}/docs/ssot/{domain}/SPEC.md` | 현재 이 도메인이 **할 수 있는 것**을 코드 기준으로 정확히 기술하는 진실원 |
| **walk** | `apps/{app}/docs/walk/{domain}/feature/{slug}/` | 기능 개발이 진행되는 작업 공간 |
| **레지스트리** | `apps/{app}/docs/DOMAIN.md`, 루트 `docs/DOMAIN.md` | 도메인 ↔ 경로 매핑 |

**핵심 규칙**: 어떤 도메인의 "현재 능력"에 대한 진실은 오직 그 도메인의 `SPEC.md` 하나뿐이다. 코드가 진실이고, `SPEC.md`는 그 코드를 사람이/에이전트가 읽기 쉽게 고정한 스냅샷이다. 둘이 어긋나면 `SPEC.md`를 코드에 맞춘다.

## 2. ssot 도메인 산출물 (4종 파일)

한 도메인의 진실원은 `ssot/{domain}/` 폴더 아래 **4개 파일**로 구성된다. 서로 다른 "고도(altitude)"를 담당하며 내용이 중복되지 않는다.

| 파일 | 담는 것 (altitude) | 담지 않는 것 |
|------|-------------------|-------------|
| `SPEC.md` | **능력/계약** — 무엇을 할 수 있나. API·동작·불변식·경계 | 도메인 모델 내부 구조 |
| `CONTEXT.md` | **바운디드 컨텍스트** — 경계, 다른 컨텍스트와의 관계(컨텍스트 맵), 통합 지점 | 개별 필드/능력 |
| `MODEL.md` | **도메인 모델** — Aggregate·Entity·Value Object·도메인 이벤트·영속 매핑 | API 응답 DTO(그건 SPEC) |
| `LANGUAGE.md` | **유비쿼터스 언어** — 용어 → 정의 → 코드 식별자 용어집 | 정의 없는 원문 나열 |

각 파일은 아래 헤더로 시작한다.
```markdown
# {Domain} — {SPEC|CONTEXT|MODEL|LANGUAGE}
- 상태: seeded | template
- 최종 동기화: {날짜} / 커밋 {sha}
```

### 파일별 고정 섹션
- **SPEC.md**: Purpose / Capabilities / API surface / Data model(응답 DTO) / Invariants & rules / Dependencies / Out of scope / Source refs
- **CONTEXT.md**: 경계(포함/제외) / 컨텍스트 맵(upstream·downstream, 관계 유형) / 통합 지점(이벤트·API) / Published language(발행 이벤트)
- **MODEL.md**: Aggregates(루트·불변식) / Entities / Value Objects / 도메인 이벤트 / 영속 매핑(테이블)
- **LANGUAGE.md**: 표 `용어 | 정의 | 코드 식별자`

### 서브도메인
도메인은 얼마든지 서브도메인을 가질 수 있다. 폴더 중첩으로 표현하며 4종 파일을 동일하게 가진다.
`ssot/document/{SPEC,CONTEXT,MODEL,LANGUAGE}.md` + `ssot/document/revision/{...}.md`.
레지스트리(`DOMAIN.md`)의 `subdomains` 열에 명시한다.

### 중복 금지 (SSoT 원칙)
같은 사실은 한 파일에만. 예: 도메인 이벤트의 스키마는 `MODEL.md`, "이벤트를 발행한다"는 능력은 `SPEC.md`, "누가 구독하나"는 `CONTEXT.md`. 응답 DTO(`IssueView`)는 `SPEC.md`의 Data model, 영속 엔티티(`issues` 테이블)는 `MODEL.md`.

## 3. walk feature 구조

```
walk/{domain}/feature/{slug}/
  plan.md        # 무엇을 왜 — 범위·완료기준
  design.md      # 어떻게 — 설계·인터페이스
  _workspace/    # 작업 중 산출물(초안, 스크래치, 중간 로그)
  report/        # 완료 보고 (결과·검증·변경 요약)
  reference/     # 참고 자료(외부 링크 메모, 발췌, 캡처)
```

`{slug}`는 kebab-case. 새 feature는 반드시 스캐폴드 스크립트로 생성한다(§5).

## 4. 라이프사이클

1. **시작** — `walk/{domain}/feature/{slug}/` 생성(스크립트), `plan.md` 작성.
2. **설계** — `design.md`에 접근·인터페이스 확정. 관련 도메인 `SPEC.md`(진실원)를 먼저 읽어 현재 능력을 파악한다.
3. **구현** — 코드 작업. 중간 산출물은 `_workspace/`.
4. **보고** — `report/`에 결과 정리.
5. **승격(promote)** — 도메인의 능력이 실제로 바뀌었으면 해당 `ssot/{domain}/SPEC.md`를 갱신하고 "최종 동기화" 줄을 갱신한다. **이것이 SSoT를 진실로 유지하는 유일한 규율이다.**

## 5. 새 feature 스캐폴딩 (양 플랫폼 공유)

```bash
node scripts/ssot/walk-new.mjs <app> <domain> <feature-slug>
```

예: `node scripts/ssot/walk-new.mjs control-plane issue attach-labels`
→ `apps/control-plane/docs/walk/issue/feature/attach-labels/` 를 `_TEMPLATE`에서 생성.

이미 존재하면 덮어쓰지 않고 중단한다.

## 6. 레지스트리 갱신 규칙

- 새 도메인/서브도메인을 만들면 **앱 레지스트리**(`apps/{app}/docs/DOMAIN.md`) 표에 행을 추가한다.
- 새 앱을 SSoT에 편입하면 **루트 인덱스**(`docs/DOMAIN.md`)에 행을 추가한다.

## 7. 멀티플랫폼 원칙

- Claude Code는 `.claude/`, Codex는 `.codex/`만 읽는다. 서로의 폴더를 읽지 않는다.
- 따라서 **실질(이 규약·스크립트·ssot·walk 문서)은 하네스 폴더 밖(`docs/`, `scripts/`)에 단 한 번** 둔다.
- 각 하네스에는 이 문서를 가리키는 **얇은 래퍼**만 둔다. 래퍼끼리 갈리는 값은 최소화한다.
- (참고) agent로 승격할 경우 본문은 동일하게 두고 `model` 프론트매터만 플랫폼별로 다르게 한다.
