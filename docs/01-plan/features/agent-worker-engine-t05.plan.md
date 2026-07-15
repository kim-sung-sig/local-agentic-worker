# [Plan] Agent Worker Engine — T05 Workspace Runtime Ownership

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t05 |
| 작성일 | 2026-07-16 |
| 상태 | Plan |
| 의존 | T04(완료 99%) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [task-05-workspace-runtime.md](../../tasks/agent-worker-engine/task-05-workspace-runtime.md) |
| 기술 스택 | Java 21 + `ProcessBuilder`(git worktree) — 신규 `runtime` bounded context |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | T04의 WORKSPACE 단계는 `prepareWorkspace` Activity를 호출하지만, 실제로 브랜치/worktree를 한 번만 만들고 재시도마다 재사용하는 런타임 구현이 없다. |
| **Solution** | `runtime.application.WorkspaceRuntime` 포트와 `runtime.infrastructure.git.GitWorktreeRuntime` 구현체를 만들어, `acquire(runId, branchName, baseBranch)`를 멱등 연산으로 제공한다. |
| **Function/UX Effect** | 직접 UI 노출 없음 — 이후 Agent Adapter가 이 컴포넌트로 실제 `WorkspaceActivity`/`ImplementationActivity`/`QualityAssuranceActivity`를 구현할 때 재사용한다. |
| **Core Value** | 스펙의 "Workflow Run은 정확히 하나의 WorkspaceRef를 생성하고, 재시도는 새 worktree/브랜치를 만들지 않는다"(Acceptance Criteria 1·2)를 실제 파일시스템/Git 수준에서 강제한다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | worktree 재생성이나 경로 이탈이 허용되면 스펙의 단일 소유권 규칙이 깨지고 동시 실행 시 충돌이 발생한다 |
| **WHO** | 향후 Agent Adapter(Implementation/QA Activity 구현체)가 이 런타임을 통해 worktree 경로를 얻는다 |
| **RISK** | 경로 검증 없이 `runId`를 그대로 경로에 사용하면 path traversal로 설정된 runtime root 밖에 파일을 쓸 수 있다 |
| **SUCCESS** | 동시/재시도 acquire가 하나의 WorkspaceRef만 반환하고, 같은 티켓의 두 번째 Run은 별도 브랜치/worktree를 가지며, path traversal 입력은 거부된다 |
| **SCOPE** | Workspace 획득/정리 런타임만 — 실제 Implementation/QA Activity 구현체는 범위 밖 |

---

## 1. Problem Statement

### 1-1. 목표 상태 (T05 완료 이후)

```
WorkspaceRuntime.acquire(runId, branchName, baseBranch)
  ├── 최초 호출: git worktree add -b {branchName} {root}/{runId} {baseBranch} 실행 → Workspace 반환
  ├── 재호출(같은 runId): 파일시스템 검증 후 git 명령 재실행 없이 동일 Workspace 반환
  └── 브랜치 불일치: 예외

WorkspaceRuntime.cleanup(runId)  ← 터미널 상태(COMPLETED/CANCELLED/FAILED)에서만 호출
  └── git worktree remove --force {root}/{runId}
```

---

## 2. Goals / Non-Goals

### Goals

- [ ] `WorkspaceRuntime` 포트 — `acquire`/`cleanup`
- [ ] `GitWorktreeRuntime` — 멱등 `acquire`, 경로 검증, 고정 배열 명령 실행(쉘 보간 없음)
- [ ] 동시/재시도 acquire가 `git worktree add`를 한 번만 실행
- [ ] runtime root 밖 경로 거부(path traversal 방어)
- [ ] `GitBranchService`는 이번 Task에서 수정하지 않음(패리티 테스트는 범위 밖 — 별도 후속 작업)

### Non-Goals

- 실제 `WorkspaceActivity` Temporal Activity 구현체 연결 — 별도 Agent Adapter 작업
- `GitBranchService` 위임 전환 — task 스펙은 "패리티 테스트 통과 후"로 조건부 명시, 이번 Task 범위에서 패리티 테스트를 별도로 만들지 않으므로 위임 전환은 보류
- 기존 `agent`/`project` BC 코드 변경

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 동일 `runId`에 대한 반복/동시 `acquire`는 `git worktree add`를 한 번만 실행하고 같은 WorkspaceRef를 반환한다 | High | Pending |
| FR-02 | 기존 worktree의 브랜치가 요청과 다르면 예외를 던진다 | High | Pending |
| FR-03 | `cleanup(runId)`은 worktree를 제거한다 | Medium | Pending |
| FR-04 | 계산된 workspace 경로가 설정된 runtime root 밖이면 거부한다 | High | Pending |
| FR-05 | 서로 다른 `runId`(같은 티켓이라도)는 별도 브랜치/worktree를 가진다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 명령 안전성 | 모든 git 명령은 고정 문자열 배열, 쉘 보간 없음 | 코드 리뷰 |
| 동시성 | 같은 runId 동시 acquire 시 경쟁 조건 없음 | 단위 테스트(동시 호출) |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 동시/재시도 acquire가 하나의 WorkspaceRef를 반환한다 (테스트)
- [ ] Implementation/QA가 동일 WorkspaceRef를 받는다 (같은 runId로 acquire 시 동일 경로 반환하는 것으로 검증)
- [ ] 같은 티켓의 두 번째 Run은 별도 브랜치/worktree를 갖는다
- [ ] `./gradlew.bat test --tests "*GitWorktreeRuntimeTest"` 통과
- [ ] `./gradlew.bat check` 통과 (기존 무관 실패 제외)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| path traversal로 runtime root 밖에 쓰기 | High | Low | `Path.normalize()` 후 `startsWith(root)` 검증, 실패 시 예외 |
| 동시 acquire 경쟁 조건으로 worktree 중복 생성 | Medium | Medium | `runId`별 락(`ConcurrentHashMap` 기반 `computeIfAbsent` 또는 `synchronized`) |
| 테스트 환경에 실제 git 미설치 | Low | Low | 테스트에서 임시 디렉토리에 `git init`으로 실제 저장소를 만들어 검증 — CI/로컬에 git CLI가 이미 필요(기존 `agent` BC도 동일 전제) |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `runtime.application.WorkspaceRuntime` | New | 포트 인터페이스 신규 |
| `runtime.infrastructure.git.GitWorktreeRuntime` | New | Git worktree 기반 구현체 신규 |

### 6.2 Current Consumers

`agent.GitBranchService`는 이번 Task에서 무변경 — 새 `runtime` BC는 완전히 격리되어 있음.

---

## 7. Next Steps

1. [ ] `/pdca design agent-worker-engine-t05`
2. [ ] TDD로 `/pdca do agent-worker-engine-t05`
3. [ ] `/pdca analyze agent-worker-engine-t05` — 90점 미만 시 최대 2회 반복

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
