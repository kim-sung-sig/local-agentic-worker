package com.example.worker.notification.application.dto;
import com.example.worker.notification.domain.model.Notification; import java.util.List;
public record NotificationPage(List<Notification> items, String nextCursor) { }
