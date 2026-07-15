package com.example.worker.engine.application.contract.v1;

public record NotificationRequest(
        ActivityRequestMetadata metadata,
        String channel,
        String message,
        int version
) {
}
