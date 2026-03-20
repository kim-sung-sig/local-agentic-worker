package com.example.worker.agent.event.model;

import com.example.worker.issue.domain.model.IssueStatus;

import java.time.Instant;
import java.util.UUID;

public record IssueStatusChangedEvent(UUID issueId, IssueStatus newStatus, Instant occurredAt) {

    public static IssueStatusChangedEvent of(UUID issueId, IssueStatus newStatus) {
        return new IssueStatusChangedEvent(issueId, newStatus, Instant.now());
    }
}
