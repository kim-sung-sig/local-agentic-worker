# [Plan] Agent Worker Engine — T07 Draft PR and Merge Gate

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agent-worker-engine-t07 |
| 작성일 | 2026-07-16 |
| 상태 | Plan |
| 의존 | T06(완료 97%) |
| 참조 스펙 | [docs/specs/agent-worker-engine.md](../../specs/agent-worker-engine.md), [task-07-source-control-gate.md](../../tasks/agent-worker-engine/task-07-source-control-gate.md) |
| 기술 스택 | Java 21 + `gh` CLI(GitHub CLI) — 신규 `scm` bounded context |

### Value Delivered (4-Perspective)

| 관점 | 내용 |
|------|------|
| **Problem** | T04/T06의 REVIEW_MERGE 단계는 `manageSourceControl` Activity를 호출하지만, 실제로 QA 통과를 강제하거나 Draft PR/병합을 멱등하게 실행하는 구현체가 없다. |
| **Solution** | `scm.application.SourceControlPlugin` 포트와 `scm.infrastructure.github.GitHubCliSourceControlPlugin` 구현체를 만들어, QA 통과 여부를 명시적으로 요구하고 멱등키로 중복 실행을 방지한다. |
| **Function/UX Effect** | 직접 UI 노출 없음 — 이후 Agent Adapter가 이 플러그인으로 실제 `SourceControlActivity`를 구현할 때 재사용한다. |
| **Core Value** | 스펙의 "기준을 충족한 결과만 Draft PR을 만들 수 있고, 최종 승인 없이는 병합할 수 없다"(Acceptance Criteria 5)를 코드 수준에서 강제한다. |

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | QA 검증이나 최종 승인 없이 PR이 생성·병합되면 스펙의 "사람이 통제 가능한 개발 워크플로"라는 핵심 가치가 깨진다 |
| **WHO** | 향후 Agent Adapter(SourceControlActivity 구현체)가 이 플러그인을 통해 실제 GitHub 연동을 수행한다 |
| **RISK** | base 브랜치를 하드코딩하거나 멱등키 없이 재시도하면 잘못된 브랜치로 PR이 생성되거나 중복 PR/병합이 발생한다 |
| **SUCCESS** | 실패/부재한 QA로는 PR을 만들 수 없고, 병합 전에는 반드시 Draft PR이 존재하며 미승인 상태에서는 병합을 호출할 수 없고, 반복 호출은 멱등키로 기존 결과를 반환한다 |
| **SCOPE** | Draft PR 생성/조회/병합 플러그인만 — 실제 GitHub API 인증이나 Ticket Sync는 범위 밖 |

---

## 1. Problem Statement

### 1-1. 목표 상태 (T07 완료 이후)

```
SourceControlPlugin
  ├── createDraftPullRequest(command)  — qaPassed=false면 예외, 멱등키로 중복 생성 방지
  ├── getPullRequest(workspacePath, branchName) — 존재 여부/상태 조회
  └── mergePullRequest(command) — Draft PR 존재 필수, 멱등키로 중복 병합 방지

AgentWorkerWorkflow: REVIEW_MERGE 단계에서 CREATE_DRAFT_PR은 QA 게이트 통과 후에만,
MERGE는 사람의 approve() 신호 이후에만 호출됨을 순서 보장 테스트로 고정
```

---

## 2. Goals / Non-Goals

### Goals

- [ ] `SourceControlPlugin` 포트 — `createDraftPullRequest`/`getPullRequest`/`mergePullRequest`
- [ ] `GitHubCliSourceControlPlugin` — `gh` CLI 기반 구현, 명령 실행기를 주입 가능하게 설계(테스트에서 실제 CLI 없이 명령 인자 검증)
- [ ] `qaPassed` 플래그로 QA 미통과 시 PR 생성 자체를 거부
- [ ] base 브랜치는 항상 호출자가 넘긴 값을 사용 — `"main"` 하드코딩 없음
- [ ] 멱등키로 반복 호출 시 기존 PR/병합 결과 반환(명령 재실행 없음)
- [ ] `AgentWorkerWorkflowTest`에 "MERGE는 approve 전에 호출되지 않는다" 순서 보장 테스트 추가

### Non-Goals

- 실제 GitHub 인증/네트워크 연동 테스트 — 명령 실행기를 목(mock)으로 대체
- `PullRequestService` 위임 전환 — task 스펙은 "패리티 테스트 통과 후"로 조건부, 이번 Task에서 패리티 테스트를 만들지 않으므로 보류(T05의 `GitBranchService`와 동일한 결정)
- Ticket Sync, 실제 워크플로-플러그인 연결(Activity 구현체) — 범위 밖

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `qaPassed=false`인 요청으로는 Draft PR을 생성할 수 없다 | High | Pending |
| FR-02 | base 브랜치는 호출자가 지정한 값을 그대로 사용한다(하드코딩 없음) | High | Pending |
| FR-03 | 같은 멱등키로 반복 `createDraftPullRequest`는 명령을 재실행하지 않고 기존 결과를 반환한다 | High | Pending |
| FR-04 | Draft PR이 존재하지 않으면 `mergePullRequest`는 거부된다 | High | Pending |
| FR-05 | 같은 멱등키로 반복 `mergePullRequest`는 명령을 재실행하지 않고 기존 결과를 반환한다 | High | Pending |
| FR-06 | Workflow의 MERGE 액션은 REVIEW_MERGE 게이트의 `approve()` 이후에만 호출된다 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 명령 안전성 | 고정 배열 명령, force push나 로컬 직접 병합 없음 | 코드 리뷰 |
| 테스트 격리 | 실제 `gh`/네트워크 호출 없이 명령 인자만 검증 | 주입 가능한 명령 실행기 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 실패/부재 QA로는 PR 생성 불가
- [ ] 병합 전 Draft PR 필수, 미승인 실행은 병합 호출 불가
- [ ] 반복 호출은 멱등키로 기존 결과 반환
- [ ] `./gradlew.bat test --tests "*GitHubCliSourceControlPluginTest" --tests "*AgentWorkerWorkflowTest"` 통과
- [ ] `./gradlew.bat check` 통과 (기존 무관 실패 제외)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 실제 `gh` CLI 의존으로 테스트가 네트워크/인증에 실패 | High | High | 명령 실행기를 인터페이스로 분리해 테스트에서 mock 주입 |
| base 브랜치 하드코딩 재발 | Medium | Low | 호출자 파라미터만 사용하고 상수 `"main"`을 코드에 두지 않음 — 코드 리뷰로 고정 |
| 멱등키 없이 재시도 시 중복 PR/병합 | High | Low | in-memory 캐시(`ConcurrentHashMap`)로 T05와 동일한 패턴 재사용 |

---

## 6. Impact Analysis

### 6.1 Changed Resources

| Resource | Type | Change Description |
|----------|------|--------------------|
| `scm.application.SourceControlPlugin` | New | 포트 인터페이스 신규 |
| `scm.infrastructure.github.GitHubCliSourceControlPlugin` | New | `gh` CLI 기반 구현체 신규 |
| `engine.workflow.AgentWorkerWorkflowTest` | Modify | MERGE 순서 보장 테스트 추가 |

### 6.2 Current Consumers

`agent.PullRequestService`는 이번 Task에서 무변경 — 새 `scm` BC는 완전히 격리.

---

## 7. Next Steps

1. [ ] `/pdca design agent-worker-engine-t07`
2. [ ] TDD로 `/pdca do agent-worker-engine-t07`
3. [ ] `/pdca analyze agent-worker-engine-t07` — 90점 미만 시 최대 2회 반복

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-07-16 | Initial draft | Claude |
