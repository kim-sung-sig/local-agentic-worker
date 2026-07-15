# [Analysis] Agent Worker Engine — T05 Workspace Runtime Ownership

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | worktree 재생성이나 경로 이탈이 허용되면 스펙의 단일 소유권 규칙이 깨지고 동시 실행 시 충돌이 발생한다 |
| **SUCCESS** | 동시/재시도 acquire가 하나의 WorkspaceRef만 반환하고, 같은 티켓의 두 번째 Run은 별도 브랜치/worktree를 가지며, path traversal 입력은 거부된다 |
| **SCOPE** | Workspace 획득/정리 런타임만 |

---

## Match Rate

Static-only 분석 — `Overall = Structural×0.2 + Functional×0.4 + Contract×0.4`

| Axis | Score |
|------|:-----:|
| Structural | 100% |
| Functional | 97% |
| Contract | 100% |
| **Overall Match Rate** | **99%** |

## 구조 (Structural) — 100%

Design §7.1의 3개 산출물(`WorkspaceRuntime`+`Workspace` record, `GitWorktreeRuntime`, 테스트) 전부 정확한 위치에 존재. 의사코드의 메서드명(`runGit`/`sourceRepoPath`/`currentBranchOf`)과 실제 구현(`run`/`sourceRepo`/인라인 `git branch --show-current`)이 다르지만 기능적으로 동일 — 문서 표기 차이일 뿐.

## 기능 (Functional) — 97%

- **동시/재시도 acquire 단일화(FR-01)**: `acquire`가 `synchronized`이며 in-memory `acquired` 맵을 파일시스템/git 접근보다 먼저 확인 — 동시 acquire 테스트로 검증됨
- **Implementation/QA 동일 WorkspaceRef(FR-05 유사)**: 같은 runId+branch 재호출 시 캐시된 동일 `Workspace` 객체 반환
- **runId 기반 경로 분리(FR-05)**: 경로가 `ticketId`가 아닌 `runId`로 계산되어 서로 다른 Run은 항상 별도 경로/브랜치를 가짐
- **경로 검증이 git 명령보다 선행(FR-04)**: `resolveWorkspacePath`가 `normalize()` 후 `startsWith(runtimeRoot)` 검증을 git 명령 실행 전에 수행, 위반 시 `IllegalArgumentException`
- **고정 배열 명령(NFR)**: `run(Path, String... cmd)`이 가변인자만 사용, 문자열 연결로 셸 명령을 구성하지 않음
- **cleanup은 추적된 워크스페이스만 제거**: `acquired.remove(runId)`가 null이면 즉시 반환 — 무작정 파일시스템을 삭제하지 않음
- **감점 사유**: cleanup 이후 같은 runId로 재획득하는 시나리오, 그리고 파일시스템엔 있지만 in-memory 캐시가 비어있는 재시작 복구 분기(`Files.isDirectory` true + 캐시 miss)가 각각 별도 테스트로 커버되지 않음(Minor)

## 계약 (Contract) — 100%

- `GitWorktreeRuntime`이 `WorkspaceRuntime`을 정확히 구현(`acquire`/`cleanup` 시그니처, `Workspace(runId, path, branchName)` 형태)
- `GitBranchService`는 이번 Task에서 `WorkspaceRuntime`/`GitWorktreeRuntime`을 전혀 참조하지 않음 — Plan의 Non-Goal(위임 전환 보류)을 그대로 준수. (참고: 이 파일이 git status상 `M`으로 표시되지만, 세션 시작 이전부터 있던 무관한 변경이며 T05 범위 밖)

## TDD 프로세스 확인

Design §6.1의 7개 시나리오 전부 테스트로 매핑됨. Windows 짧은 경로명(8.3) vs 정규 경로명 문제를 `Path.toRealPath()` 정규화로 해결한 것은 테스트 인프라 수정이며, 프로덕션 코드(`GitWorktreeRuntime`)의 실제 버그를 가리는 우회가 아님을 확인 — 이미 저장되는 경로는 정규화된 절대경로.

## Plan Success Criteria (§4.1) 평가

| Criterion | Status | Evidence |
|-----------|:------:|----------|
| 동시/재시도 acquire → 하나의 WorkspaceRef | ✅ Met | `synchronized acquire`, 캐시 우선 확인 |
| Implementation/QA 동일 WorkspaceRef | ✅ Met | 같은 runId 재호출 시 캐시 반환 |
| 같은 티켓의 두 번째 Run → 별도 브랜치/worktree | ✅ Met | 경로가 runId 기반 |
| `test --tests "*GitWorktreeRuntimeTest"` 통과 | ✅ Met | 7/7 통과 |
| `./gradlew.bat check` 통과 (기존 무관 실패 제외) | ✅ Met | 기존 무관 실패 7건만 존재 |

**5/5 충족.**

## Gaps

Critical/Important 없음.

**Minor**:
1. cleanup 이후 같은 runId 재획득 시나리오 미검증
2. 파일시스템엔 있으나 in-memory 캐시가 비어있는 재시작 복구 분기(`GitWorktreeRuntime.java:43-49`) 미검증
3. `GitBranchService.java`의 기존 `M` 변경은 세션 시작 전부터 존재하던 것으로 T05와 무관함을 확인

## Decision

**Match Rate 99% (>= 90% 기준 충족)** — 반복 개선 불필요. T05 완료로 판단하고 T06(Implementation and QA loop)으로 진행.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial analysis — Match Rate 99% | Claude |
