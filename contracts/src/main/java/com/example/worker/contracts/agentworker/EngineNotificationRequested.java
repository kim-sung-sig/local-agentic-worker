package com.example.worker.contracts.agentworker;

import java.time.Instant;

/**
 * Published by Agent Engine when a workflow run wants to notify a project's operators.
 * Control Plane consumes this on {@link #TOPIC} and resolves {@code ticketId} to a
 * {@code projectId} itself — Agent Engine never looks up Issue/Notification state directly.
 */
public record EngineNotificationRequested(
        String workflowRunId,
        String ticketId,
        String type,
        String severity,
        String title,
        String message,
        String idempotencyKey,
        Instant occurredAt
) {

    public static final String TOPIC = "engine-notification-requested";
}
