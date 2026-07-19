package com.example.worker.agent.application.service;

import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.application.port.AgentLogStore;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentJobStatus;
import com.example.worker.agent.domain.model.AgentPhase;
import com.example.worker.agent.event.model.AgentPhaseRequestedEvent;
import com.example.worker.agent.infrastructure.config.AgentProperties;
import com.example.worker.agent.infrastructure.stream.InMemoryAgentLogStore;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.*;
import com.example.worker.issue.event.model.IssueRejectedEvent;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AgentWorkerService")
@ExtendWith(MockitoExtension.class)
class AgentWorkerServiceTest {

    @Mock AgentJobRepository agentJobRepository;
    @Mock GitBranchService gitBranchService;
    @Mock PullRequestService pullRequestService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock IssueRepository issueRepository;
    @Mock ProjectRepository projectRepository;

    AgentLogStore logStore;
    AgentWorkerService service;
    AgentProperties props;
    List<AgentJobStatus> savedStatuses;

    @BeforeEach
    void setUp() {
        savedStatuses = new ArrayList<>();
        logStore = new InMemoryAgentLogStore();
        props = new AgentProperties();
        props.setCliPath("claude");
        props.setTimeoutMinutes(1);

        String passResponse = "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"test-session\"}\n"
                + "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"matchRate: 95%\\nPASS\",\"session_id\":\"test-session\"}";

        ClaudeAgentExecutor executor = new ClaudeAgentExecutor(props,
                (workDir, cmd) -> passResponse, logStore);

        service = new AgentWorkerService(agentJobRepository, gitBranchService,
                executor, pullRequestService, eventPublisher, issueRepository, projectRepository, props);

        when(agentJobRepository.save(any())).thenAnswer(inv -> {
            AgentJob job = inv.getArgument(0);
            savedStatuses.add(job.getStatus());
            return job;
        });
    }

    private void stubDraftPr() {
        when(pullRequestService.createDraftPr(any(), any(), any(), any(), any()))
                .thenReturn("https://github.com/org/repo/pull/1");
    }

    private AgentPhaseRequestedEvent planEvent() {
        return new AgentPhaseRequestedEvent(
                UUID.randomUUID(), AgentPhase.PLAN,
                UUID.randomUUID(), System.getProperty("java.io.tmpdir"), "main",
                1, "테스트 ��능 추가", "설명", "MEDIUM");
    }

    private AgentPhaseRequestedEvent developEvent() {
        return new AgentPhaseRequestedEvent(
                UUID.randomUUID(), AgentPhase.DEVELOP,
                UUID.randomUUID(), System.getProperty("java.io.tmpdir"), "main",
                1, "테스트 기능 추가", "설명", "MEDIUM");
    }

    @Nested
    @DisplayName("PLAN 페이즈")
    class PlanPhase {

        @Test
        @DisplayName("PLAN 트리거 시 PLANNING → SUCCEEDED 순으로 저장된다")
        void plan_savesCorrectStatusTransitions() {
            service.handlePhaseRequested(planEvent());

            assertThat(savedStatuses).containsSubsequence(
                    AgentJobStatus.PLANNING,
                    AgentJobStatus.SUCCEEDED);
        }
    }

    @Nested
    @DisplayName("DEVELOP 페이즈")
    class DevelopPhase {

        @Test
        @DisplayName("DEVELOP 트리거 시 PENDING → CODING → VERIFYING → SUCCEEDED 순으로 저장된다")
        void develop_savesCorrectStatusTransitions() {
            stubDraftPr();

            service.handlePhaseRequested(developEvent());

            assertThat(savedStatuses).containsSubsequence(
                    AgentJobStatus.PENDING,
                    AgentJobStatus.CODING,
                    AgentJobStatus.VERIFYING,
                    AgentJobStatus.SUCCEEDED);
        }
    }

    @Nested
    @DisplayName("handleRejected() — 반려 재시도")
    class HandleRejected {

        private UUID issueIdValue;
        private UUID projectIdValue;
        private IssueId issueId;
        private ProjectId projectId;

        @BeforeEach
        void setUpRejected() {
            issueIdValue = UUID.randomUUID();
            projectIdValue = UUID.randomUUID();
            issueId = IssueId.of(issueIdValue);
            projectId = ProjectId.of(projectIdValue);

            Issue issue = Issue.reconstitute(issueId, projectId, new IssueNumber(1),
                    "버그 수정", "설명", Priority.HIGH, IssueStatus.IN_REVIEW, LocalDateTime.now());

            AgentJob existingJob = AgentJob.reconstitute(
                    AgentJobId.newId(), issueIdValue, projectIdValue,
                    "feat/issue-1-bug-fix", AgentPhase.DEVELOP, AgentJobStatus.SUCCEEDED,
                    Instant.now(), Instant.now(), null, "https://github.com/pr/1", null);

            Project project = Project.reconstitute(projectId, "test-project",
                    System.getProperty("java.io.tmpdir"), "main", LocalDateTime.now());

            when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
            when(agentJobRepository.findByIssueId(issueIdValue)).thenReturn(List.of(existingJob));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
            stubDraftPr();
        }

        @Test
        @DisplayName("반려 시 새 AgentJob이 PLANNING 상태로 저장된다")
        void handleRejected_savesCorrectStatusTransitions() {
            IssueRejectedEvent event = new IssueRejectedEvent(issueId, "테스트 코드 누락", 1);

            service.handleRejected(event);

            assertThat(savedStatuses).contains(AgentJobStatus.PLANNING);
        }

        @Test
        @DisplayName("반려 시 save가 최소 1회 이상 호출된다")
        void handleRejected_executorReruns() {
            IssueRejectedEvent event = new IssueRejectedEvent(issueId, "피드백", 1);

            service.handleRejected(event);

            verify(agentJobRepository, atLeastOnce()).save(any());
        }
    }
}
