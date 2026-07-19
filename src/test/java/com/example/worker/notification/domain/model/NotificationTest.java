package com.example.worker.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("알림")
class NotificationTest {

    @Test
    @DisplayName("생성자는 발행자를 SYSTEM으로 고정하고 읽음은 최초 시각을 보존한다")
    void createFixesPublisherAndKeepsFirstReadTime() {
        Notification notification = Notification.create(UUID.randomUUID(), null, "event-key",
                NotificationType.WORKFLOW_STATUS_CHANGED, NotificationSeverity.INFO, "제목", "내용");
        Instant firstReadAt = Instant.parse("2026-07-18T00:00:00Z");

        notification.markRead(firstReadAt);
        notification.markRead(firstReadAt.plusSeconds(1));

        assertThat(notification.publisher()).isEqualTo("SYSTEM");
        assertThat(notification.readAt()).isEqualTo(firstReadAt);
    }
}
