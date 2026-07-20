package com.example.worker.notification.infrastructure.kafka;

import com.example.worker.contracts.agentworker.EngineNotificationRequested;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.notification.application.dto.CreateNotificationCommand;
import com.example.worker.notification.application.service.NotificationCommandService;
import com.example.worker.notification.domain.model.NotificationSeverity;
import com.example.worker.notification.domain.model.NotificationType;
import com.example.worker.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EngineNotificationConsumer")
class EngineNotificationConsumerTest {

    private IssueRepository issueRepository;
    private NotificationCommandService notificationCommandService;
    private EngineNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        notificationCommandService = mock(NotificationCommandService.class);
        consumer = new EngineNotificationConsumer(issueRepository, notificationCommandService);
    }

    @Test
    @DisplayName("Engine이 발행한 알림 이벤트를 소비하면 ticketId로 Issue를 조회해 Notification을 생성한다")
    void consume_resolvesIssueAndCreatesNotification() {
        UUID ticketId = UUID.randomUUID();
        UUID workflowRunAggregateId = UUID.randomUUID();
        Issue issue = Issue.create(ProjectId.newId(), 1, "title", "description", Priority.MEDIUM);
        when(issueRepository.findById(IssueId.of(ticketId))).thenReturn(Optional.of(issue));

        EngineNotificationRequested event = new EngineNotificationRequested(
                workflowRunAggregateId.toString(), ticketId.toString(),
                "ACTIVITY_COMPLETED", "INFO", "QA 통과", "QA 점수 95",
                "wf:QA:1:ACTIVITY_COMPLETED:QA 통과:QA 점수 95",
                Instant.parse("2026-07-16T00:00:00Z"));

        consumer.consume(event);

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationCommandService).create(captor.capture());
        CreateNotificationCommand command = captor.getValue();
        assertThat(command.projectId()).isEqualTo(issue.getProjectId().value());
        assertThat(command.workflowRunId()).isEqualTo(workflowRunAggregateId);
        assertThat(command.eventKey()).isEqualTo("wf:QA:1:ACTIVITY_COMPLETED:QA 통과:QA 점수 95");
        assertThat(command.type()).isEqualTo(NotificationType.ACTIVITY_COMPLETED);
        assertThat(command.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(command.title()).isEqualTo("QA 통과");
        assertThat(command.message()).isEqualTo("QA 점수 95");
    }

    @Test
    @DisplayName("ticketId에 해당하는 Issue가 없으면 예외를 던진다")
    void consume_whenIssueNotFound_throws() {
        UUID ticketId = UUID.randomUUID();
        when(issueRepository.findById(IssueId.of(ticketId))).thenReturn(Optional.empty());

        EngineNotificationRequested event = new EngineNotificationRequested(
                UUID.randomUUID().toString(), ticketId.toString(),
                "ACTIVITY_COMPLETED", "INFO", "title", "message", "key",
                Instant.parse("2026-07-16T00:00:00Z"));

        assertThatThrownBy(() -> consumer.consume(event)).isInstanceOf(IllegalStateException.class);
    }
}
