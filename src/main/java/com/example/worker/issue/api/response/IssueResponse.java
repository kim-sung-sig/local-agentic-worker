package com.example.worker.issue.api.response;

import com.example.worker.issue.application.dto.IssueSummary;

import java.time.LocalDateTime;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        int issueNumber,
        String title,
        String description,
        String priority,
        String status,
        LocalDateTime createdAt
) {
    public static IssueResponse from(IssueSummary summary) {
        return new IssueResponse(
                summary.id(),
                summary.projectId(),
                summary.issueNumber(),
                summary.title(),
                summary.description(),
                summary.priority(),
                summary.status(),
                summary.createdAt()
        );
    }
}
