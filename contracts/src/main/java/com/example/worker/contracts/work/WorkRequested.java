package com.example.worker.contracts.work;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record WorkRequested(
        UUID issueId,
        UUID projectId,
        URI repositoryUri,
        String baseBranch,
        String rawSpecification,
        Instant occurredAt
) {

    public String workflowId() {
        return "issue-" + issueId;
    }
}
