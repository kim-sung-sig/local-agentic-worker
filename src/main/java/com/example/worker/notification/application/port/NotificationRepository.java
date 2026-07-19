package com.example.worker.notification.application.port;

import com.example.worker.notification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    List<Notification> findAfter(UUID projectId, long cursor, int limit);
    List<Notification> findLatest(UUID projectId, long before, int limit);
    Optional<Notification> findByNotificationIdAndProjectId(UUID notificationId, UUID projectId);
    Optional<Notification> findByEventKey(String eventKey);
    long countUnread(UUID projectId);
    boolean hasExpiredBefore(UUID projectId, long cursor, Instant cutoff);
    boolean existsCursor(UUID projectId, long cursor);
    void deleteCreatedBefore(Instant cutoff);
}
