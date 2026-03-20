package com.example.worker.issue.api.request;

import com.example.worker.issue.domain.model.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIssueStatusRequest(
        @NotNull IssueStatus status
) {}
