package com.example.worker.notification.application.dto;
import com.example.worker.notification.domain.model.*; import java.util.UUID;
public record CreateNotificationCommand(UUID projectId, UUID workflowRunId, String eventKey, NotificationType type, NotificationSeverity severity, String title, String message) { }
