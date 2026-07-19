package com.example.worker.contracts.agentworker;

public record StageExecutionIdentity(
        String workflowRunId,
        String stage,
        int attemptNumber,
        int stageExecutionGeneration
) {

    public String idempotencyKey() {
        return workflowRunId + ":" + stage + ":" + attemptNumber + ":" + stageExecutionGeneration;
    }
}
