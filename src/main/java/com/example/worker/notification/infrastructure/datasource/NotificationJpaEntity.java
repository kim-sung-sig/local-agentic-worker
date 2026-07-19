package com.example.worker.notification.infrastructure.datasource;

import com.example.worker.notification.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "project_notification")
class NotificationJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "notification_id", nullable = false) private UUID notificationId;
    @Column(name = "event_key", nullable = false) private String eventKey;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "workflow_run_id") private UUID workflowRunId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationSeverity severity;
    @Column(nullable = false) private String publisher;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String message;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected NotificationJpaEntity() { }
    static NotificationJpaEntity from(Notification n) { NotificationJpaEntity e=new NotificationJpaEntity(); e.id=n.id(); e.notificationId=n.notificationId(); e.eventKey=n.eventKey(); e.projectId=n.projectId(); e.workflowRunId=n.workflowRunId(); e.type=n.type(); e.severity=n.severity(); e.publisher=n.publisher(); e.title=n.title(); e.message=n.message(); e.readAt=n.readAt(); e.createdAt=n.createdAt(); return e; }
    Notification toDomain() { return Notification.reconstitute(id, notificationId, eventKey, projectId, workflowRunId, type, severity, publisher, title, message, readAt, createdAt); }
}
