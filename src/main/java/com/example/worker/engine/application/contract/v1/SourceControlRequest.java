package com.example.worker.engine.application.contract.v1;

public record SourceControlRequest(
        ActivityRequestMetadata metadata,
        WorkspaceRef workspaceRef,
        String action,
        int version
) {
}
