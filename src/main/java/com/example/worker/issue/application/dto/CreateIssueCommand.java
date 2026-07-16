package com.example.worker.issue.application.dto;

import com.example.worker.issue.domain.model.Priority;

import java.util.UUID;

public record CreateIssueCommand(
        UUID projectId,
        String title,
        String description,
        Priority priority
) {
}
