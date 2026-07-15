package com.example.worker.engine.application.contract.v1;

import com.example.worker.engine.domain.model.WorkflowStage;

public record ActivityRequestMetadata(String workflowRunId, WorkflowStage stage, int attemptNumber, int version) {

    public String idempotencyKey() {
        return workflowRunId + ":" + stage + ":" + attemptNumber;
    }
}
