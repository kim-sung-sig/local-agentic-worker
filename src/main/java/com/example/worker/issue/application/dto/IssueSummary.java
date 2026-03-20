package com.example.worker.issue.application.dto;

import com.example.worker.issue.domain.model.Issue;

import java.time.LocalDateTime;
import java.util.UUID;

public record IssueSummary(
        UUID id,
        UUID projectId,
        int issueNumber,
        String title,
        String description,
        String priority,
        String status,
        LocalDateTime createdAt
) {
    public static IssueSummary from(Issue issue) {
        return new IssueSummary(
                issue.getId().value(),
                issue.getProjectId().value(),
                issue.getIssueNumber().value(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getPriority().name(),
                issue.getStatus().name(),
                issue.getCreatedAt()
        );
    }
}
