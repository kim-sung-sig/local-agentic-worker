package com.example.worker.project.application.dto;

public record ProjectRegistrationCommand(
        String name,
        String repositoryUri,
        String baseBranch,
        String credentialRef
) {
}
