package com.example.worker.notification.infrastructure.kafka;

import com.example.worker.contracts.agentworker.EngineNotificationRequested;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.notification.application.dto.CreateNotificationCommand;
import com.example.worker.notification.application.service.NotificationCommandService;
import com.example.worker.notification.domain.model.NotificationSeverity;
import com.example.worker.notification.domain.model.NotificationType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@link EngineNotificationRequested}, published by Agent Engine, and resolves it into a
 * Control Plane {@link CreateNotificationCommand}. Agent Engine only knows {@code ticketId}; Control
 * Plane owns the Issue/Notification lookup so the two apps share no direct method call.
 */
@Component
public class EngineNotificationConsumer {

    private final IssueRepository issueRepository;
    private final NotificationCommandService notificationCommandService;

    public EngineNotificationConsumer(IssueRepository issueRepository,
                                       NotificationCommandService notificationCommandService) {
        this.issueRepository = issueRepository;
        this.notificationCommandService = notificationCommandService;
    }

    @KafkaListener(topics = EngineNotificationRequested.TOPIC, groupId = "control-plane")
    public void consume(EngineNotificationRequested event) {
        UUID ticketId = UUID.fromString(event.ticketId());
        Issue issue = issueRepository.findById(IssueId.of(ticketId))
                .orElseThrow(() -> new IllegalStateException("Issue not found: " + ticketId));

        notificationCommandService.create(new CreateNotificationCommand(
                issue.getProjectId().value(),
                UUID.fromString(event.workflowRunId()),
                event.idempotencyKey(),
                NotificationType.valueOf(event.type()),
                NotificationSeverity.valueOf(event.severity()),
                event.title(),
                event.message()));
    }
}
