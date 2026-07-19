package com.example.worker.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Notification {
    public static final String SYSTEM = "SYSTEM";
    private final Long id;
    private final UUID notificationId;
    private final String eventKey;
    private final UUID projectId;
    private final UUID workflowRunId;
    private final NotificationType type;
    private final NotificationSeverity severity;
    private final String publisher;
    private final String title;
    private final String message;
    private Instant readAt;
    private final Instant createdAt;

    private Notification(Long id, UUID notificationId, String eventKey, UUID projectId, UUID workflowRunId, NotificationType type,
                         NotificationSeverity severity, String publisher, String title, String message,
                         Instant readAt, Instant createdAt) {
        this.id = id; this.notificationId = notificationId; this.eventKey = eventKey; this.projectId = projectId; this.workflowRunId = workflowRunId;
        this.type = type; this.severity = severity; this.publisher = publisher; this.title = title; this.message = message;
        this.readAt = readAt; this.createdAt = createdAt;
    }
    public static Notification create(UUID projectId, UUID workflowRunId, String eventKey, NotificationType type,
                                      NotificationSeverity severity, String title, String message) {
        return new Notification(null, UUID.randomUUID(), eventKey, projectId, workflowRunId, type, severity, SYSTEM, title, message, null, Instant.now());
    }
    public static Notification reconstitute(Long id, UUID notificationId, String eventKey, UUID projectId, UUID workflowRunId,
                                             NotificationType type, NotificationSeverity severity, String publisher,
                                             String title, String message, Instant readAt, Instant createdAt) {
        return new Notification(id, notificationId, eventKey, projectId, workflowRunId, type, severity, publisher, title, message, readAt, createdAt);
    }
    public void markRead(Instant at) { if (readAt == null) readAt = at; }
    public Long id() { return id; } public UUID notificationId() { return notificationId; } public String eventKey() { return eventKey; } public UUID projectId() { return projectId; }
    public UUID workflowRunId() { return workflowRunId; } public NotificationType type() { return type; }
    public NotificationSeverity severity() { return severity; } public String publisher() { return publisher; }
    public String title() { return title; } public String message() { return message; } public Instant readAt() { return readAt; }
    public Instant createdAt() { return createdAt; }
}
