package com.example.worker.notification.api.request; import jakarta.validation.constraints.*; import java.util.*;
public record MarkNotificationsReadRequest(@NotEmpty @Size(max=100) List<UUID> notificationIds) { }
