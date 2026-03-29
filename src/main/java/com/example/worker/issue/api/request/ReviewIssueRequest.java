package com.example.worker.issue.api.request;

import jakarta.validation.constraints.NotNull;

public record ReviewIssueRequest(
        @NotNull Boolean approved,
        String feedback
) {}
