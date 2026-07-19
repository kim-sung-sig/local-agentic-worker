package com.example.worker.project.domain.model;

public record RemoteProjectRegistration(
        String name,
        RepositoryUri repositoryUri,
        BranchName baseBranch,
        String credentialRef
) {

    public RemoteProjectRegistration {
        credentialRef = credentialRef == null || credentialRef.isBlank() ? null : credentialRef;
    }
}
