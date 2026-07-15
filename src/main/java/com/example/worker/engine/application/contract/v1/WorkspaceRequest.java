package com.example.worker.engine.application.contract.v1;

public record WorkspaceRequest(
        ActivityRequestMetadata metadata,
        String changeType,
        String featureSlug,
        int version
) {
}
