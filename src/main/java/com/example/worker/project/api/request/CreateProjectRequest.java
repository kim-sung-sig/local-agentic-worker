package com.example.worker.project.api.request;

import com.example.worker.project.application.dto.ProjectRegistrationCommand;
import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String repositoryUri,
        @NotBlank String baseBranch,
        String credentialRef
) {
    public ProjectRegistrationCommand toCommand() {
        return new ProjectRegistrationCommand(name, repositoryUri, baseBranch, credentialRef);
    }
}
