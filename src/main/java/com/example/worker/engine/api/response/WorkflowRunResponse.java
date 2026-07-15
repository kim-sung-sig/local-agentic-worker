package com.example.worker.engine.api.response;

public record WorkflowRunResponse(String workflowRunId, String currentStage, String status) {
}
