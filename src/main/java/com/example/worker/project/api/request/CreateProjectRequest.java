package com.example.worker.project.api.request;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String localPath,
        @NotBlank String baseBranch
) {}
