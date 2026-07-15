# [Design] Agent Worker Engine — T05 Workspace Runtime Ownership

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | worktree 재생성이나 경로 이탈이 허용되면 스펙의 단일 소유권 규칙이 깨지고 동시 실행 시 충돌이 발생한다 |
| **SUCCESS** | 동시/재시도 acquire가 하나의 WorkspaceRef만 반환하고, 같은 티켓의 두 번째 Run은 별도 브랜치/worktree를 가지며, path traversal 입력은 거부된다 |
| **SCOPE** | Workspace 획득/정리 런타임만 |

---

## 1. Overview

### 1.1 Design Goals

- `acquire`를 완전히 멱등하게 만든다 — 파일시스템에 이미 유효한 worktree가 있으면 git 명령을 재실행하지 않는다.
- `runId`별로 in-memory 락을 걸어 동시 acquire가 `git worktree add`를 중복 실행하지 못하게 한다.
- 경로 계산은 항상 `Path.normalize()` 후 설정된 root 하위인지 검증한다 — 검증 실패 시 git 명령을 아예 실행하지 않는다.

### 1.2 Architecture Decision

`agent` BC의 `ProcessRunner`/`CommandRunner`는 package-private이라 재사용할 수 없고, T05는 새로운 격리된 `runtime` BC이므로 자체 프로세스 실행 유틸을 갖는다(기존 `ProcessRunner`와 동일한 안전 패턴 — 고정 배열 명령, virtual thread로 stdout 드레인, timeout). 별도 3안 비교 없이 기존 프로젝트 관례를 그대로 재현한다.

---

## 2. Component Diagram

```
runtime.application
  └── WorkspaceRuntime (interface)
        ├── acquire(runId, branchName, baseBranch) -> Workspace
        └── cleanup(runId)
        record Workspace(runId, path, branchName)

runtime.infrastructure.git
  └── GitWorktreeRuntime implements WorkspaceRuntime
        ├── ConcurrentHashMap<String, Workspace> acquired  (in-memory idempotency cache + per-run lock)
        ├── resolvePath(runId) -> Path  (root 하위 검증)
        └── ProcessBuilder 기반 git 명령 실행 (고정 배열)
```

## 3. Workspace 계산 및 검증

```java
private Path resolveWorkspacePath(String runId) {
    Path candidate = runtimeRoot.resolve(runId).normalize();
    if (!candidate.startsWith(runtimeRoot)) {
        throw new IllegalArgumentException("Workspace path escapes runtime root: " + runId);
    }
    return candidate;
}
```

`runtimeRoot`는 생성자에서 이미 `.normalize()`된 절대 경로로 보관한다. `runId`에 `..`, 절대경로 등이 섞여 있어도 `resolve().normalize()` 이후 `startsWith(runtimeRoot)` 검사로 탈출을 차단한다.

## 4. acquire 로직 (멱등성)

```java
public synchronized Workspace acquire(String runId, String branchName, String baseBranch) {
    Workspace existing = acquired.get(runId);
    if (existing != null) {
        if (!existing.branchName().equals(branchName)) {
            throw new IllegalStateException(
                "Workspace for run " + runId + " already bound to branch " + existing.branchName());
        }
        return existing;
    }

    Path path = resolveWorkspacePath(runId);
    if (Files.isDirectory(path)) {
        // 재시작 후 재획득 등 — 파일시스템에는 있지만 in-memory 캐시가 비어있는 경우
        String currentBranch = currentBranchOf(path);
        if (!currentBranch.equals(branchName)) {
            throw new IllegalStateException(
                "Existing worktree at " + path + " is on branch " + currentBranch
                    + ", expected " + branchName);
        }
    } else {
        runGit(sourceRepoPath, "git", "worktree", "add", "-b", branchName,
                path.toString(), baseBranch);
    }

    Workspace workspace = new Workspace(runId, path.toString(), branchName);
    acquired.put(runId, workspace);
    return workspace;
}
```

`synchronized`로 메서드 전체를 감싸 동시 호출 시 같은 `runId`든 다른 `runId`든 순차 처리한다(worktree 생성 자체가 빈번한 hot path가 아니므로 단순 락으로 충분 — Design 결정, 세밀한 runId별 락은 이번 규모에 과설계).

## 5. cleanup

```java
public synchronized void cleanup(String runId) {
    Workspace workspace = acquired.remove(runId);
    if (workspace == null) {
        return;
    }
    runGit(sourceRepoPath, "git", "worktree", "remove", "--force", workspace.path());
}
```

호출 시점(터미널 상태 도달 여부)은 호출자(향후 T06/T07)의 책임 — `GitWorktreeRuntime` 자신은 Workflow 상태를 알지 못한다.

## 6. Test Plan (TDD)

### 6.1 GitWorktreeRuntimeTest — 실제 임시 Git 저장소 사용

| # | Test | Expected |
|---|------|----------|
| 1 | 최초 `acquire` | worktree 디렉터리 생성, 지정 브랜치로 체크아웃됨 |
| 2 | 같은 `runId`로 반복 `acquire` | 동일 `Workspace` 반환, `git worktree add`가 다시 실행되지 않음(worktree 목록에 중복 없음) |
| 3 | 같은 `runId`, 다른 `branchName`으로 `acquire` | `IllegalStateException` |
| 4 | 다른 `runId` 두 번 `acquire` | 서로 다른 경로/브랜치의 `Workspace` 2개 |
| 5 | `runId`에 `../../etc` 같은 path traversal 입력 | `IllegalArgumentException`, git 명령 미실행 |
| 6 | `cleanup(runId)` | worktree 디렉터리 제거됨, 이후 같은 `runId` `acquire` 시 새 worktree 재생성 |
| 7 | 동시 `acquire`(같은 runId, 여러 스레드) | 정확히 하나의 `Workspace`만 생성, `git worktree add` 1회만 실행 |

### 6.2 테스트 픽스처

`@TempDir`로 임시 디렉터리를 만들고 `git init`으로 소스 저장소를 초기화, 최초 커밋 1개 생성 후 `GitWorktreeRuntime(sourceRepoPath, runtimeRoot)`를 직접 생성(Spring 컨텍스트 불필요)해 테스트한다.

---

## 7. Implementation Guide

### 7.1 File Structure

```
src/main/java/com/example/worker/runtime/
├── application/
│   └── WorkspaceRuntime.java
└── infrastructure/git/
    └── GitWorktreeRuntime.java

src/test/java/com/example/worker/runtime/infrastructure/git/
└── GitWorktreeRuntimeTest.java
```

### 7.2 Implementation Order (TDD)

1. [ ] `WorkspaceRuntime` 인터페이스 + `Workspace` record 정의
2. [ ] `GitWorktreeRuntimeTest` 작성 — §6.1의 7개 시나리오 먼저 (Red)
3. [ ] `GitWorktreeRuntime` 구현 — 경로 검증 → 멱등 acquire → cleanup (Green)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
