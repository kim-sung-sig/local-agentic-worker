package com.example.worker.engine.application.contract.v1;

public record WorkspaceResponse(
        WorkspaceRef workspaceRef,
        String branchName,
        int version
) {
}
