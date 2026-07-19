package com.example.worker.engine.application.contract.v1;

public record NotificationRequest(
        ActivityRequestMetadata metadata,
        String ticketId,
        String type,
        String severity,
        String title,
        String message,
        int version
) {
}
