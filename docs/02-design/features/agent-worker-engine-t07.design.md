# [Design] Agent Worker Engine — T07 Draft PR and Merge Gate

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | QA 검증이나 최종 승인 없이 PR이 생성·병합되면 스펙의 "사람이 통제 가능한 개발 워크플로"라는 핵심 가치가 깨진다 |
| **SUCCESS** | 실패/부재한 QA로는 PR을 만들 수 없고, 병합 전에는 반드시 Draft PR이 존재하며 미승인 상태에서는 병합을 호출할 수 없고, 반복 호출은 멱등키로 기존 결과를 반환한다 |
| **SCOPE** | Draft PR 생성/조회/병합 플러그인만 |

---

## 1. Overview

### 1.1 Architecture Decision — 자체 DTO vs T03 계약 재사용

T03의 `SourceControlRequest`(metadata, workspaceRef, action, version)는 `action` 문자열 하나로 여러 오퍼레이션을 뭉뚱그리고, base 브랜치·QA 통과 여부·제목/본문 같은 이번 Task에 필요한 세부 필드가 없다. 이를 억지로 확장하면 T03 계약(이미 배포됨)을 건드리게 되므로, T05가 `runtime.application.WorkspaceRuntime`에 독자적인 `Workspace` record를 둔 선례를 그대로 따라 `scm` BC도 자신만의 커맨드/결과 record를 갖는다(엔진 계약을 건드리지 않고, 이후 Agent Adapter가 T03 계약 ↔ 이 플러그인 사이를 매핑하는 얇은 어댑터를 추가하면 된다 — 그 매핑은 범위 밖).

`qaPassed` 불리언을 커맨드에 직접 포함시켜 "QA 통과 필수"를 플러그인이 자체적으로 강제하도록 설계한다 — 워크플로가 실수로 일찍 호출하더라도 플러그인이 방어선 역할을 한다.

### 1.2 명령 실행기 주입

`gh` CLI는 실제 GitHub 인증/네트워크가 필요해 T05(로컬 git)처럼 실제 CLI로 테스트할 수 없다. `GitHubCliSourceControlPlugin`은 명령 실행 로직을 `CommandExecutor` 함수형 인터페이스로 분리해 생성자로 주입받고, 기본 생성자는 실제 `ProcessBuilder` 실행기를 사용한다. 테스트는 목(mock) `CommandExecutor`로 명령 인자만 검증한다.

---

## 2. Component Diagram

```
scm.application
  └── SourceControlPlugin (interface)
        ├── createDraftPullRequest(CreateDraftPullRequestCommand) -> PullRequestResult
        ├── getPullRequest(workspacePath, branchName) -> PullRequestResult
        └── mergePullRequest(MergePullRequestCommand) -> PullRequestResult
        record CreateDraftPullRequestCommand(idempotencyKey, workspacePath, baseBranch, branchName, title, body, qaPassed)
        record MergePullRequestCommand(idempotencyKey, workspacePath, branchName)
        record PullRequestResult(url, status)  // status: "DRAFT" | "MERGED" | null(없음)

scm.infrastructure.github
  └── GitHubCliSourceControlPlugin implements SourceControlPlugin
        ├── CommandExecutor (nested functional interface)
        ├── ConcurrentHashMap<String, PullRequestResult> resultsByIdempotencyKey
        └── ObjectMapper (gh pr view --json 출력 파싱)
```

## 3. 오퍼레이션 로직

```java
public PullRequestResult createDraftPullRequest(CreateDraftPullRequestCommand command) {
    if (!command.qaPassed()) {
        throw new IllegalStateException("Cannot create a draft PR without a passed QA attempt");
    }
    PullRequestResult cached = resultsByIdempotencyKey.get(command.idempotencyKey());
    if (cached != null) {
        return cached;
    }
    String output = commandExecutor.execute(command.workspacePath(),
            "gh", "pr", "create", "--draft",
            "--base", command.baseBranch(), "--head", command.branchName(),
            "--title", command.title(), "--body", command.body());
    PullRequestResult result = new PullRequestResult(extractUrl(output), "DRAFT");
    resultsByIdempotencyKey.put(command.idempotencyKey(), result);
    return result;
}

public PullRequestResult getPullRequest(String workspacePath, String branchName) {
    String output = commandExecutor.execute(workspacePath,
            "gh", "pr", "view", branchName, "--json", "url,state");
    return parsePullRequestJson(output); // 없으면 null 반환(예외 아님 — 조회는 실패가 정상 경로)
}

public PullRequestResult mergePullRequest(MergePullRequestCommand command) {
    PullRequestResult cached = resultsByIdempotencyKey.get(command.idempotencyKey());
    if (cached != null && "MERGED".equals(cached.status())) {
        return cached;
    }
    PullRequestResult existing = getPullRequest(command.workspacePath(), command.branchName());
    if (existing == null) {
        throw new IllegalStateException(
                "Cannot merge: no draft PR exists for branch " + command.branchName());
    }
    commandExecutor.execute(command.workspacePath(),
            "gh", "pr", "merge", command.branchName(), "--merge");
    PullRequestResult result = new PullRequestResult(existing.url(), "MERGED");
    resultsByIdempotencyKey.put(command.idempotencyKey(), result);
    return result;
}
```

`base` 브랜치는 항상 `command.baseBranch()`에서만 읽는다 — 클래스 어디에도 `"main"` 리터럴 상수를 두지 않는다.

## 4. Workflow 순서 보장 (기존 T04/T06 구조 재확인)

`AgentWorkerWorkflowImpl.handleReviewMerge`는 이미 다음 순서를 강제한다(T04에서 구현됨, 변경 없음):

```
1. manageSourceControl(action=CREATE_DRAFT_PR)  — QA 게이트를 통과해야 도달하는 REVIEW_MERGE 단계에서만 호출
2. awaitGate(REVIEW_MERGE) — approve() 대기
3. manageSourceControl(action=MERGE)  — 2번이 승인된 경우에만 도달
```

이번 Task는 코드를 바꾸지 않고, `AgentWorkerWorkflowTest`에 `InOrder` 검증 테스트를 추가해 "MERGE는 approve 이전에 스케줄되지 않는다"는 보장을 명시적으로 고정한다(T07 Test Method의 명시적 요구사항).

---

## 5. Test Plan (TDD)

### 5.1 GitHubCliSourceControlPluginTest

| # | Test | Expected |
|---|------|----------|
| 1 | 정상 create 호출 | `commandExecutor`에 `--draft --base {baseBranch} --head {branchName} ...` 인자가 정확히 전달됨(다른 base 브랜치, 예: `develop`로 검증 — `"main"` 하드코딩 아님을 증명) |
| 2 | `qaPassed=false`로 create 호출 | `IllegalStateException`, `commandExecutor` 미호출 |
| 3 | 같은 idempotencyKey로 create 반복 호출 | 두 번째 호출은 `commandExecutor` 미실행, 첫 결과와 동일한 `PullRequestResult` 반환 |
| 4 | Draft PR 없이 merge 호출 (`getPullRequest`가 null 반환) | `IllegalStateException`, merge 명령 미실행 |
| 5 | Draft PR 존재 시 merge 호출 | `gh pr merge {branchName} --merge` 인자로 실행됨 |
| 6 | 같은 idempotencyKey로 merge 반복 호출 | 두 번째 호출은 merge 명령 미실행, 첫 결과 반환 |
| 7 | `getPullRequest`가 JSON 출력을 파싱 | `url`/`state` 필드가 올바르게 매핑됨 |

### 5.2 AgentWorkerWorkflowTest 추가

| # | Test | Expected |
|---|------|----------|
| 8 | MERGE는 approve 이전에 스케줄되지 않는다 | `InOrder` 검증: `runQualityAssurance` → `manageSourceControl(CREATE_DRAFT_PR)`이 `approve()` 호출 전에 발생, `manageSourceControl(MERGE)`는 `approve()` 이후에만 발생 |

---

## 6. Implementation Guide

### 6.1 File Structure

```
src/main/java/com/example/worker/scm/
├── application/
│   └── SourceControlPlugin.java
└── infrastructure/github/
    └── GitHubCliSourceControlPlugin.java

src/test/java/com/example/worker/scm/infrastructure/github/
└── GitHubCliSourceControlPluginTest.java

src/test/java/com/example/worker/engine/workflow/
└── AgentWorkerWorkflowTest.java (modify — 시나리오 8 추가)
```

### 6.2 Implementation Order (TDD)

1. [ ] `SourceControlPlugin` 인터페이스 + record 정의
2. [ ] `GitHubCliSourceControlPluginTest` 작성 — §5.1의 7개 시나리오 먼저 (Red)
3. [ ] `GitHubCliSourceControlPlugin` 구현 (Green)
4. [ ] `AgentWorkerWorkflowTest`에 순서 보장 테스트 추가 (기존 구조로 이미 Green이어야 함 — 새 회귀 없이 보장을 문서화)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
