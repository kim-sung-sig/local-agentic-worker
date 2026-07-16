package com.example.worker.issue.api.request;

import com.example.worker.issue.application.dto.CreateIssueCommand;
import com.example.worker.issue.domain.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateIssueRequest(
        @NotBlank String title,
        String description,
        @NotNull Priority priority
) {
    public CreateIssueCommand toCommand(UUID projectId) {
        return new CreateIssueCommand(projectId, title, description, priority);
    }
}
