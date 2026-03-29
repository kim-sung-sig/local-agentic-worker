package com.example.worker.agent.application.service;

import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.application.port.AgentLogStore;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobStatus;
import com.example.worker.agent.infrastructure.config.AgentProperties;
import com.example.worker.agent.infrastructure.stream.InMemoryAgentLogStore;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
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

    AgentLogStore logStore;
    AgentWorkerService service;

    List<AgentJobStatus> savedStatuses;

    @BeforeEach
    void setUp() {
        savedStatuses = new ArrayList<>();
        logStore = new InMemoryAgentLogStore();
        AgentProperties props = new AgentProperties();
        props.setCliPath("claude");
        props.setTimeoutMinutes(1);

        ClaudeAgentExecutor executor = new ClaudeAgentExecutor(props,
                (workDir, cmd) -> "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"done\"}",
                logStore);

        service = new AgentWorkerService(agentJobRepository, gitBranchService,
                executor, pullRequestService, eventPublisher);

        // 각 save 시점의 status 스냅샷을 기록 (mutable 객체 참조 문제 방지)
        when(agentJobRepository.save(any())).thenAnswer(inv -> {
            AgentJob job = inv.getArgument(0);
            savedStatuses.add(job.getStatus());
            return job;
        });
        when(pullRequestService.createDraftPr(any(), any(), any(), any(), any()))
                .thenReturn("https://github.com/org/repo/pull/1");
    }

    private IssueCreatedEvent event() {
        return new IssueCreatedEvent(
                UUID.randomUUID(), 1, "테스트 기능 추가", "설명", "MEDIUM",
                UUID.randomUUID(), "/tmp/project", "main", java.time.Instant.now());
    }

    @Nested
    @DisplayName("상태 전환 순서")
    class StatusTransitions {

        @Test
        @DisplayName("handle() 실행 시 PLANNING → CODING → SUCCEEDED 순으로 저장된다")
        void handle_savesCorrectStatusTransitions() {
            service.handle(event());

            assertThat(savedStatuses).containsSubsequence(
                    AgentJobStatus.PLANNING,
                    AgentJobStatus.CODING,
                    AgentJobStatus.SUCCEEDED);
        }
    }
}
