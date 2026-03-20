# [Check] Agentic Worker — Gap Analysis

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | agentic-worker |
| 분석일 | 2026-03-20 |
| Match Rate | **91%** (전체) / **96%** (Phase 1 핵심) |
| 설계 항목 수 | 55 (파일 + 설정 기준) |
| 구현 항목 수 | 50 |
| 미구현 항목 | 5 |
| 기능 편차 | 3 (개선 방향) |

---

## 1. 매칭 결과 요약

```
[Plan] ✅ → [Design] ✅ → [Do] ✅ → [Check] 🔄 → [Act] ⏳

Phase 1 핵심 (52개): 50/52 = 96.2% ✅
전체 (55개):        50/55 = 90.9% ✅
```

> Phase 1 핵심 기준 96.2%로 90% 임계값 초과. 미구현 5개 중 3개는 설계에서 "다음 단계(skeleton)"로 명시된 항목.

---

## 2. 매칭 상세 분석

### 2-1. Project BC (✅ 14/15)

| 파일 | 상태 | 비고 |
|------|------|------|
| `project/domain/model/Project.java` | ✅ | |
| `project/domain/model/ProjectId.java` | ✅ | |
| `project/domain/model/LocalPath.java` | ✅ | 검증 예외: `BusinessException` 사용 (설계의 `IllegalArgumentException` 대비 개선) |
| `project/domain/model/BranchName.java` | ✅ | |
| `project/application/service/ProjectCommandService.java` | ✅ | |
| `project/application/service/ProjectQueryService.java` | ✅ | `getProject()` 반환 타입 편차 (하단 3-1 참고) |
| `project/application/dto/ProjectSummary.java` | ✅ | |
| `project/application/dto/ProjectDetail.java` | ❌ **MISSING** | |
| `project/application/port/ProjectRepository.java` | ✅ | |
| `project/infrastructure/datasource/ProjectJpaEntity.java` | ✅ | |
| `project/infrastructure/datasource/ProjectJpaRepository.java` | ✅ | |
| `project/infrastructure/datasource/ProjectRepositoryAdapter.java` | ✅ | |
| `project/api/controller/ProjectController.java` | ✅ | |
| `project/api/request/CreateProjectRequest.java` | ✅ | |
| `project/api/response/ProjectResponse.java` | ✅ | |

### 2-2. Issue BC (✅ 16/17)

| 파일 | 상태 | 비고 |
|------|------|------|
| `issue/domain/model/Issue.java` | ✅ | 상태 전이 메서드 편차 (하단 3-2 참고) |
| `issue/domain/model/IssueId.java` | ✅ | |
| `issue/domain/model/IssueNumber.java` | ✅ | |
| `issue/domain/model/IssueStatus.java` | ✅ | |
| `issue/domain/model/Priority.java` | ✅ | |
| `issue/application/service/IssueCommandService.java` | ✅ | issueNumber 채번 인라인 처리 (하단 3-3 참고) |
| `issue/application/service/IssueQueryService.java` | ✅ | |
| `issue/application/service/IssueNumberSequenceService.java` | ❌ **MISSING** | 로직은 IssueCommandService에 인라인 포함 |
| `issue/application/dto/IssueSummary.java` | ✅ | |
| `issue/application/port/IssueRepository.java` | ✅ | |
| `issue/event/model/IssueCreatedEvent.java` | ✅ | `@Externalized("issue-created")` 정확히 구현 |
| `issue/infrastructure/datasource/IssueJpaEntity.java` | ✅ | |
| `issue/infrastructure/datasource/IssueJpaRepository.java` | ✅ | |
| `issue/infrastructure/datasource/IssueRepositoryAdapter.java` | ✅ | |
| `issue/api/controller/IssueController.java` | ✅ | |
| `issue/api/request/CreateIssueRequest.java` | ✅ | |
| `issue/api/request/UpdateIssueStatusRequest.java` | ✅ | |
| `issue/api/response/IssueResponse.java` | ✅ | |

### 2-3. Agent BC (✅ 1/4)

| 파일 | 상태 | 비고 |
|------|------|------|
| `agent/infrastructure/kafka/IssueCreatedEventConsumer.java` | ✅ | `@KafkaListener` 로깅 구현 |
| `agent/domain/model/AgentJob.java` | ❌ MISSING | 설계 §9: "다음 단계 준비용 — 현재는 skeleton" |
| `agent/domain/model/AgentJobStatus.java` | ❌ MISSING | 설계 §9: skeleton |
| `agent/application/service/AgentWorkerService.java` | ❌ MISSING | 설계 §9: skeleton |

> Agent BC 3개 미구현 항목은 설계 원문에서 명시적으로 "다음 단계(skeleton)"로 분류된 항목.

### 2-4. Common (✅ 5/5)

| 파일 | 상태 |
|------|------|
| `common/exception/BusinessException.java` | ✅ |
| `common/exception/ErrorCode.java` | ✅ |
| `common/exception/ErrorResponse.java` | ✅ |
| `common/exception/GlobalExceptionHandler.java` | ✅ |
| `common/web/SpaForwardController.java` | ✅ |

### 2-5. Vue 3 CDN UI (✅ 8/8)

| 파일 | 상태 |
|------|------|
| `static/index.html` | ✅ |
| `static/css/app.css` | ✅ |
| `static/js/api.js` | ✅ |
| `static/js/app.js` | ✅ |
| `static/js/router.js` | ✅ |
| `static/js/components/ProjectList.js` | ✅ |
| `static/js/components/ProjectForm.js` | ✅ |
| `static/js/components/IssueList.js` | ✅ |
| `static/js/components/IssueForm.js` | ✅ |
| `static/js/components/IssueDetail.js` | ✅ |

### 2-6. 설정 파일 (✅ 6/6)

| 파일 | 상태 |
|------|------|
| `application.yml` | ✅ |
| `db/migration/V1__init_schema.sql` | ✅ |
| `build.gradle` (Flyway 추가) | ✅ |

---

## 3. 기능 편차 분석

### 3-1. ProjectDetail DTO 미생성 → ProjectSummary 통합 사용

| 항목 | 설계 | 구현 | 평가 |
|------|------|------|------|
| `ProjectQueryService.getProject()` 반환 | `ProjectDetail` | `ProjectSummary` | ⚠️ 편차 |
| 원인 | 설계에 ProjectDetail 정의됨 | ProjectSummary로 통합 | 단순화는 긍정적, API 계약 불일치는 수정 필요 |

**권고**: `ProjectDetail.java` 추가 또는 설계 문서에서 `ProjectDetail` 제거 (합의 필요).

### 3-2. Issue 상태 전이 메서드 통합

| 항목 | 설계 | 구현 | 평가 |
|------|------|------|------|
| 전이 메서드 | `startProgress()`, `markInReview()`, `markFailed()`, `close()` 4개 분리 | `updateStatus(IssueStatus)` 단일 메서드 + `isValidTransition()` 검증 | ✅ 개선 |
| 이유 | 명명된 메서드는 직관적이나 검증 로직 중복 가능성 | 통합 + 검증 = 유지보수 우수 | |

**권고**: 현재 구현 유지. 설계 문서 업데이트 권장.

### 3-3. IssueNumberSequenceService 통합

| 항목 | 설계 | 구현 | 평가 |
|------|------|------|------|
| issueNumber 채번 | 별도 `IssueNumberSequenceService` 클래스 | `IssueCommandService` 내 인라인 | ⚠️ 구조적 편차 |
| 실제 로직 | `SELECT COALESCE(MAX(issue_number), 0) + 1` | 동일 쿼리 인라인 실행 | 기능 동일 |

**권고**: 기능상 문제없음. `IssueCommandService`가 커질 경우 분리 검토.

---

## 4. Gap 목록 (수정 필요 항목)

| # | 항목 | 우선순위 | 설명 |
|---|------|---------|------|
| G-1 | `ProjectDetail.java` 미생성 | **HIGH** | `ProjectQueryService.getProject()` 반환 타입 불일치 유발 |
| G-2 | `IssueNumberSequenceService.java` 미생성 | LOW | 기능 동일, 구조만 설계와 다름 |
| G-3 | `AgentJob.java` 미생성 | LOW | 설계에서 "다음 단계" 명시 — Phase 2 구현 대상 |
| G-4 | `AgentJobStatus.java` 미생성 | LOW | G-3과 동일 |
| G-5 | `AgentWorkerService.java` 미생성 | LOW | G-3과 동일 |

---

## 5. Phase 2 준비 항목 (다음 단계)

설계 §6-3에서 예고된 항목:

```java
// agent BC - 다음 단계 구현 대상
agent/domain/model/AgentJob.java          // QUEUED / RUNNING / SUCCEEDED / FAILED
agent/domain/model/AgentJobStatus.java
agent/application/service/AgentWorkerService.java
// └─ GitBranchService.createBranch()
// └─ ClaudeAgentExecutor.execute()
// └─ PullRequestService.createDraftPr()
```

---

## 6. 결론 및 권고

### 매치율 평가

| 기준 | 매치율 | 판정 |
|------|--------|------|
| Phase 1 핵심 (52개) | **96.2%** | ✅ 통과 |
| 전체 (55개, skeleton 포함) | **90.9%** | ✅ 통과 |

### 권고 조치

1. **즉시 (G-1)**: `ProjectDetail.java` 추가 또는 설계 수정 (ProjectSummary 통합 결정)
2. **선택적 (G-2)**: `IssueNumberSequenceService` 분리 여부 팀 합의
3. **Phase 2 (G-3~G-5)**: Agent BC skeleton 구현 (GitBranchService, ClaudeAgentExecutor 등)

### 다음 단계

- Match Rate 96.2% (Phase 1 기준) — `/pdca report agentic-worker`로 완료 보고서 생성 가능
- 또는 G-1 수정 후 재분석: `ProjectDetail.java` 추가 → `/pdca analyze agentic-worker`
