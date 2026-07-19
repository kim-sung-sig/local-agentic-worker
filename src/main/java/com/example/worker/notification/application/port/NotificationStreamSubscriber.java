package com.example.worker.notification.application.port;

import com.example.worker.notification.application.dto.NotificationStreamEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

public interface NotificationStreamSubscriber {
    SseEmitter subscribe(UUID projectId);
    void replay(SseEmitter emitter, NotificationStreamEvent event);
    void reset(SseEmitter emitter);
}
