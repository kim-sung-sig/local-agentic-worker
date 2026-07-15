# [Analysis] Agent Worker Engine — T07 Draft PR and Merge Gate

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | QA 검증이나 최종 승인 없이 PR이 생성·병합되면 스펙의 "사람이 통제 가능한 개발 워크플로"라는 핵심 가치가 깨진다 |
| **SUCCESS** | 실패/부재한 QA로는 PR을 만들 수 없고, 병합 전에는 반드시 Draft PR이 존재하며 미승인 상태에서는 병합을 호출할 수 없고, 반복 호출은 멱등키로 기존 결과를 반환한다 |
| **SCOPE** | Draft PR 생성/조회/병합 플러그인만 |

---

## Match Rate

Static-only 분석 — `Overall = Structural×0.2 + Functional×0.4 + Contract×0.4`

| Axis | Score |
|------|:-----:|
| Structural | 100% |
| Functional | 100% |
| Contract | 100% |
| **Overall Match Rate** | **100%** |

## 구조 (Structural) — 100%

Design §6.1의 산출물(`SourceControlPlugin`+3개 record, `GitHubCliSourceControlPlugin`+`CommandExecutor`, 테스트, `AgentWorkerWorkflowTest` 수정) 전부 정확히 일치.

## 기능 (Functional) — 100%

- **BC 격리(a)**: `scm` 패키지 어디에도 `engine.application.contract.v1` import 없음 — T05의 `runtime.Workspace` 선례를 그대로 따른 자체 DTO 설계가 검증됨
- **QA 게이트가 부작용보다 우선(b)**: `qaPassed=false` 검증이 캐시 조회·명령 실행보다 먼저 수행됨, 거부 시 명령 미실행 확인
- **Draft-before-merge 이중 보장(c)**: 플러그인 레벨(`getPullRequest` null이면 거부)과 워크플로 레벨(`InOrder` 검증으로 MERGE가 approve 이전에 스케줄되지 않음) 둘 다 확인
- **멱등성(d)**: 두 오퍼레이션 모두 `ConcurrentHashMap` 캐시로 반복 호출 시 명령 재실행 없음 — 반복 create는 명령 1회, 반복 merge는 view+merge 쌍 1회만 실행
- **base 브랜치 하드코딩 없음(e)**: 플러그인 코드에 `"main"`/`"force"`/로컬 `git merge` 문자열 전무, `--base`는 항상 `command.baseBranch()`만 사용, 테스트가 `"develop"`로 이를 증명
- **명령 안전성(f)**: `ProcessBuilder`에 가변인자 배열만 전달(문자열 연결 없음), force push나 로컬 병합 없이 `gh pr merge`(API 매개 병합)만 사용

## 계약 (Contract) — 100%

- `GitHubCliSourceControlPlugin`이 `SourceControlPlugin`의 3개 메서드를 정확히 구현
- `CommandExecutor` 함수형 인터페이스로 DI 구성 — 기본 생성자는 실제 `ProcessBuilder` 실행기, 테스트는 mock 주입
- `PullRequestService`는 이번 Task에서 무변경 — Plan의 Non-Goal(T05의 `GitBranchService`와 동일한 위임 보류 결정)을 그대로 준수

## TDD 프로세스 확인

Design §5.1의 7개 시나리오 전부 커버(JSON 파싱은 정상/PR없음 두 케이스로 분리되어 테스트 8개), §5.2 시나리오 8(MERGE 순서 보장)도 `InOrder`+`argThat`으로 정확히 구현.

## Plan Success Criteria (§4.1) 평가

| Criterion | Status | Evidence |
|-----------|:------:|----------|
| 실패/부재 QA로 PR 생성 불가 | ✅ Met | `createDraftPullRequest`의 qaPassed 검증 |
| 병합 전 Draft PR 필수, 미승인 시 병합 불가 | ✅ Met | 플러그인 + 워크플로 이중 검증 |
| 반복 호출은 멱등키로 기존 결과 반환 | ✅ Met | `ConcurrentHashMap` 캐시, 테스트로 명령 재실행 없음 확인 |
| 대상 테스트 스위트 통과 | ✅ Met | 전부 통과 |
| `./gradlew.bat check` 통과 (기존 무관 실패 제외) | ✅ Met | 105개 중 기존 무관 실패 7건만 존재 |

**5/5 완전 충족.**

## Gaps

없음. Critical/Important/Minor 전부 0건.

**긍정적 개선 사항(Gap 아님)**:
- `createDraftPullRequest`/`mergePullRequest`에 `synchronized` 추가 — Design 의사코드보다 한 단계 더 나아가 check-then-put 복합 연산의 경쟁 조건을 방어
- JSON 파싱 테스트를 정상/PR없음 두 케이스로 분리해 Design의 7개 시나리오보다 촘촘한 커버리지(8개 테스트) 확보

## Decision

**Match Rate 100%** — 반복 개선 불필요. T07 완료로 판단하고 마지막 Task인 T08(API and integration QA)로 진행.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial analysis — Match Rate 100% | Claude |
