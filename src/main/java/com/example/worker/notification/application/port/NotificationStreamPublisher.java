package com.example.worker.notification.application.port;
import com.example.worker.notification.application.dto.NotificationStreamEvent;
public interface NotificationStreamPublisher { void publishCreated(NotificationStreamEvent notification); void publishRead(NotificationStreamEvent notification); }
