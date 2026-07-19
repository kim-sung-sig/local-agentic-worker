package com.example.worker.notification.application.dto;

import com.example.worker.notification.domain.model.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationStreamEvent(String eventId, UUID notificationId, UUID projectId, UUID workflowRunId,
                                      String type, String severity, String publisher, String title, String message,
                                      Instant createdAt, Instant readAt) {
    public static NotificationStreamEvent from(Notification n) {
        return new NotificationStreamEvent(String.valueOf(n.id()), n.notificationId(), n.projectId(), n.workflowRunId(),
                n.type().name(), n.severity().name(), n.publisher(), n.title(), n.message(), n.createdAt(), n.readAt());
    }
}
