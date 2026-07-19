package com.example.worker.contracts.agentworker;

import java.net.URI;
import java.util.UUID;

public record ProjectExecutionSnapshot(
        UUID projectId,
        URI repositoryUri,
        String baseBranch,
        String credentialRef,
        String sourceCommit
) {
}
