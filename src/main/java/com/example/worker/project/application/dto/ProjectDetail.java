package com.example.worker.project.application.dto;

import com.example.worker.project.domain.model.Project;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectDetail(UUID id, String name, String localPath, String baseBranch, LocalDateTime createdAt) {

    public static ProjectDetail from(Project project) {
        return new ProjectDetail(
                project.getId().value(),
                project.getName(),
                project.getLocalPath().value(),
                project.getBaseBranch().value(),
                project.getCreatedAt()
        );
    }
}
