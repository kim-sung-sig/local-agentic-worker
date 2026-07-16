package com.example.worker.project.api.response;

import com.example.worker.project.application.dto.ProjectDetail;
import com.example.worker.project.application.dto.ProjectSummary;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String repositoryUri,
        String baseBranch,
        LocalDateTime createdAt
) {
    public static ProjectResponse from(ProjectSummary summary) {
        return new ProjectResponse(
                summary.id(),
                summary.name(),
                summary.repositoryUri(),
                summary.baseBranch(),
                summary.createdAt()
        );
    }

    public static ProjectResponse from(ProjectDetail detail) {
        return new ProjectResponse(
                detail.id(),
                detail.name(),
                detail.repositoryUri(),
                detail.baseBranch(),
                detail.createdAt()
        );
    }
}
