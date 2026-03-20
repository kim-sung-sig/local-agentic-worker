package com.example.worker.agent.event.model;

import java.time.Instant;
import java.util.UUID;

public record IssueStatusChangedEvent(UUID issueId, String newStatus, Instant occurredAt) {

    public static IssueStatusChangedEvent of(UUID issueId, String newStatus) {
        return new IssueStatusChangedEvent(issueId, newStatus, Instant.now());
    }
}
