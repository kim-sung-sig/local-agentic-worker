package com.example.worker.issue.event.model;

import java.time.Instant;
import java.util.UUID;

public record IssueCreatedEvent(
        UUID issueId,
        int issueNumber,
        String title,
        String description,
        String priority,
        UUID projectId,
        String projectLocalPath,
        String baseBranch,
        Instant occurredAt
) {}
