package com.example.worker.agent.application.service;

import com.example.worker.issue.domain.model.*;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptBuilder")
class PromptBuilderTest {

    private static final String SLUG = "issue-1-bug-fix";

    private IssueCreatedEvent event() {
        return new IssueCreatedEvent(
                UUID.randomUUID(), 1, "버그 수정", "상세 설명", "HIGH",
                UUID.randomUUID(), System.getProperty("java.io.tmpdir"), "main", Instant.now());
    }

    private Issue issue() {
        return Issue.reconstitute(
                IssueId.newId(), ProjectId.newId(), new IssueNumber(1),
                "버그 수정", "상세 설명", Priority.HIGH,
                IssueStatus.REJECTED, LocalDateTime.now());
    }

    private Project project() {
        return Project.reconstitute(
                ProjectId.newId(), "test-project", System.getProperty("java.io.tmpdir"), "main", LocalDateTime.now());
    }

    @Nested
    @DisplayName("buildPlanPhase()")
    class BuildPlanPhase {

        @Test
        @DisplayName("/pdca plan 커맨드가 포함된다")
        void containsPdcaPlanCommand() {
            String result = PromptBuilder.buildPlanPhase(event(), SLUG);
            assertThat(result).contains("/pdca plan " + SLUG);
        }

        @Test
        @DisplayName("이슈 제목이 포함된다")
        void containsIssueTitle() {
            String result = PromptBuilder.buildPlanPhase(event(), SLUG);
            assertThat(result).contains("버그 수정");
        }

        @Test
        @DisplayName("에이전트 팀 금지 문구가 포함된다")
        void containsNoAgentTeamWarning() {
            String result = PromptBuilder.buildPlanPhase(event(), SLUG);
            assertThat(result).contains("에이전트 팀");
        }
    }

    @Nested
    @DisplayName("buildPlanRetryPhase()")
    class BuildPlanRetryPhase {

        @Test
        @DisplayName("피드백 내용이 포함된다")
        void includesFeedback() {
            String result = PromptBuilder.buildPlanRetryPhase(issue(), project(), SLUG, "테스트 코드 누락", 1);
            assertThat(result).contains("테스트 코드 누락");
        }

        @Test
        @DisplayName("재시도 번호가 포함된다")
        void includesRetryCount() {
            String result = PromptBuilder.buildPlanRetryPhase(issue(), project(), SLUG, "feedback", 2);
            assertThat(result).contains("재시도 #2");
        }
    }

    @Nested
    @DisplayName("buildDoPhase()")
    class BuildDoPhase {

        @Test
        @DisplayName("/pdca do 커맨드가 포함된다")
        void containsPdcaDoCommand() {
            String result = PromptBuilder.buildDoPhase(SLUG);
            assertThat(result).contains("/pdca do " + SLUG);
        }
    }

    @Nested
    @DisplayName("buildAnalysisPhase()")
    class BuildAnalysisPhase {

        @Test
        @DisplayName("/pdca analysis 커맨드가 포함된다")
        void containsPdcaAnalysisCommand() {
            String result = PromptBuilder.buildAnalysisPhase(SLUG);
            assertThat(result).contains("/pdca analysis " + SLUG);
        }

        @Test
        @DisplayName("matchRate 출력 형식 가이드가 포함된다")
        void containsMatchRateFormat() {
            String result = PromptBuilder.buildAnalysisPhase(SLUG);
            assertThat(result).contains("matchRate");
        }
    }

    @Nested
    @DisplayName("toFeatureSlug()")
    class ToFeatureSlug {

        @Test
        @DisplayName("이슈 번호와 제목으로 slug가 생성된다")
        void generatesSlugWithIssueNumber() {
            String slug = PromptBuilder.toFeatureSlug(3, "Add Login Feature");
            assertThat(slug).startsWith("issue-3-");
            assertThat(slug).contains("login");
        }

        @Test
        @DisplayName("특수문자는 제거된다")
        void removesSpecialCharacters() {
            String slug = PromptBuilder.toFeatureSlug(1, "Fix Bug: #123!");
            assertThat(slug).doesNotContain("#", "!", ":");
        }
    }
}
