package com.example.worker.engine.application.contract.v1;

public record ImplementationRequest(
        ActivityRequestMetadata metadata,
        WorkspaceRef workspaceRef,
        ArtifactRef implementationPlanRef,
        int version
) {
}
