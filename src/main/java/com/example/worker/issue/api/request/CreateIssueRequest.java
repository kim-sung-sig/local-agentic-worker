package com.example.worker.issue.api.request;

import com.example.worker.issue.domain.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIssueRequest(
        @NotBlank String title,
        String description,
        @NotNull Priority priority
) {}
