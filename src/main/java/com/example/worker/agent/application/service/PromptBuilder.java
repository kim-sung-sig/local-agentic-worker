package com.example.worker.agent.application.service;

import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import com.example.worker.project.domain.model.Project;

public final class PromptBuilder {

    private PromptBuilder() {}

    private static final String NO_AGENT_TEAM = """
            ## 금지사항
            - 에이전트 팀(서브에이전트, Task tool, 멀티에이전트) 사용 금지
            """;

    // ──────────────────────────────────────────────────────
    // Phase 1: PLANNING  →  /pdca plan {slug}
    // ──────────────────────────────────────────────────────

    /**
     * 신규 이슈 Plan 페이즈 프롬프트.
     * bkit이 /pdca plan을 실행하여 docs/01-plan/features/{slug}.plan.md를 생성한다.
     */
    public static String buildPlanPhase(IssueCreatedEvent event, String slug) {
        String description = event.description() == null ? "(설명 없음)" : event.description();
        return """
                /pdca plan %s

                ## 구현 대상 이슈
                | 항목 | 내용 |
                |------|------|
                | 번호 | #%d |
                | 제목 | %s |
                | 우선순위 | %s |

                ## 요구사항 (Description)
                %s

                ## 아키텍처 컨텍스트
                - 패키지 루트: `src/main/java/com/example/worker/`
                - DDD 레이어: domain → application → infrastructure → api
                - 컨벤션: `docs/conventions/CONVENTIONS.md` 참조

                %s""".formatted(
                slug,
                event.issueNumber(), event.title(), event.priority(),
                description,
                NO_AGENT_TEAM);
    }

    /**
     * 재시도 Plan 페이즈 프롬프트 (반려 피드백 반영).
     */
    public static String buildPlanRetryPhase(Issue issue, Project project, String slug, String feedback, int retryCount) {
        String description = issue.getDescription() == null ? "(설명 없음)" : issue.getDescription();
        String localPath = project.getLocalPath() != null ? project.getLocalPath().value() : "(경로 미설정)";
        return """
                /pdca plan %s

                ## 구현 대상 이슈 (재시도 #%d)
                | 항목 | 내용 |
                |------|------|
                | 번호 | #%d |
                | 제목 | %s |
                | 우선순위 | %s |
                | 프로젝트 경로 | %s |

                ## 요구사항 (Description)
                %s

                ## ⚠️ 이전 검토 피드백 (반드시 반영하여 재계획)
                ```
                %s
                ```

                ## 아키텍처 컨텍스트
                - DDD 레이어: domain → application → infrastructure → api
                - 컨벤션: `docs/conventions/CONVENTIONS.md` 참조

                %s""".formatted(
                slug,
                retryCount,
                issue.getIssueNumber().value(), issue.getTitle(), issue.getPriority(),
                localPath,
                description,
                feedback,
                NO_AGENT_TEAM);
    }

    // ──────────────────────────────────────────────────────
    // Phase 2: DESIGN  →  /pdca design {slug}
    // ──────────────────────────────────────────────────────

    /**
     * Design 페이즈 프롬프트.
     * plan 문서를 읽고 상세 설계 문서를 생성한다.
     * 이전 세션을 --resume으로 이어받아 실행된다.
     */
    public static String buildDesignPhase(String slug) {
        return """
                /pdca design %s

                ## 설계 지침
                - docs/01-plan/features/%s.plan.md 를 입력으로 사용
                - 출력: docs/02-design/features/%s.design.md
                - DDD 레이어별 클래스 목록 (domain/application/infrastructure/api)
                - API 엔드포인트 계약 (Method, Path, Request, Response)
                - 도메인 모델 상태 전이 다이어그램
                - 단위 테스트 목록 (클래스 × 시나리오)

                %s""".formatted(slug, slug, slug, NO_AGENT_TEAM);
    }

    // ──────────────────────────────────────────────────────
    // Phase 3: CODING  →  /pdca do {slug}
    // ──────────────────────────────────────────────────────

    /**
     * Do 페이즈 프롬프트 — plan 문서를 읽고 구현.
     * 이전 세션을 --resume으로 이어받아 실행된다.
     */
    public static String buildDoPhase(String slug) {
        return """
                /pdca do %s

                ## 구현 지침
                - docs/01-plan/features/%s.plan.md 의 요구사항을 정확히 구현
                - 구현 순서: 도메인 모델 → 포트 → 서비스 → 인프라 → API
                - 비즈니스 로직은 반드시 도메인 모델 내부에 위치
                - 신규 클래스에는 단위 테스트 작성 (JUnit5, Mockito)
                - @DisplayName 한국어, Given/When/Then 구조 준수

                %s""".formatted(slug, slug, NO_AGENT_TEAM);
    }

    /**
     * Do 재시도 페이즈 프롬프트 — gap analysis 실패 시 재구현.
     */
    public static String buildDoRetryPhase(String slug, String analysisOutput, int attempt) {
        return """
                /pdca do %s

                ## ⚠️ Gap Analysis 실패 — 재구현 필요 (시도 #%d)

                이전 구현에서 다음 갭이 발견되었습니다:
                ```
                %s
                ```

                위 갭을 반드시 해소하여 재구현하세요.

                ## 구현 지침
                - docs/01-plan/features/%s.plan.md 의 모든 요구사항 충족
                - 누락된 부분을 우선적으로 구현
                - 단위 테스트 보완

                %s""".formatted(slug, attempt, analysisOutput, slug, NO_AGENT_TEAM);
    }

    // ──────────────────────────────────────────────────────
    // Phase 3: VERIFYING  →  /pdca analysis {slug}
    // ──────────────────────────────────────────────────────

    /**
     * Analysis 페이즈 프롬프트 — plan vs 구현 갭 분석.
     * 이전 세션을 --resume으로 이어받아 실행된다.
     */
    public static String buildAnalysisPhase(String slug) {
        return """
                /pdca analysis %s

                ## 갭 분석 기준
                - docs/01-plan/features/%s.plan.md 의 모든 기능 요구사항(FR) 구현 여부
                - DDD 레이어 규칙 준수 여부
                - 단위 테스트 존재 여부
                - 결과는 반드시 matchRate(0-100) 수치로 표시할 것

                ## 출력 형식
                ```
                matchRate: {N}%%
                PASS / FAIL
                미구현 항목: ...
                ```""".formatted(slug, slug);
    }

    // ──────────────────────────────────────────────────────
    // 슬러그 유틸
    // ──────────────────────────────────────────────────────

    /**
     * 이슈 번호 + 제목으로 bkit feature slug 생성.
     * 브랜치명과 동일한 규칙 적용 (feat/ 접두사 제외).
     */
    public static String toFeatureSlug(int issueNumber, String title) {
        if (title == null || title.isBlank()) {
            return "issue-%d".formatted(issueNumber);
        }

        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+$", "");  // 끝의 하이픈 제거

        if (slug.isBlank()) {
            return "issue-%d".formatted(issueNumber);
        }

        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return "issue-%d-%s".formatted(issueNumber, slug);
    }
}
