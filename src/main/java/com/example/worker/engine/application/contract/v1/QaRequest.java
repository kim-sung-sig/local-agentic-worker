package com.example.worker.engine.application.contract.v1;

public record QaRequest(
        ActivityRequestMetadata metadata,
        WorkspaceRef workspaceRef,
        ArtifactRef implementationArtifactRef,
        int version
) {
}
