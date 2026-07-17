package com.example.worker.project.api.request;

import com.example.worker.project.application.dto.ProjectRegistrationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String repositoryUri,
        @NotBlank @Size(max = 100) String baseBranch,
        @Size(max = 200) String credentialRef
) {
    public ProjectRegistrationCommand toCommand() {
        return new ProjectRegistrationCommand(name, repositoryUri, baseBranch, credentialRef);
    }
}
