package com.example.worker.engine.application.contract.v1;

public record NotificationResponse(
        boolean delivered,
        int version
) {
}
