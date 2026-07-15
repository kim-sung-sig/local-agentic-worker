package com.example.worker.engine.domain.model;

import java.util.UUID;

public record WorkflowRunId(UUID value) {

    public static WorkflowRunId newId() {
        return new WorkflowRunId(UUID.randomUUID());
    }

    public static WorkflowRunId of(UUID value) {
        return new WorkflowRunId(value);
    }
}
