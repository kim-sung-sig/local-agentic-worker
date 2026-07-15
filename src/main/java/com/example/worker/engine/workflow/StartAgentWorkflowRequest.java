package com.example.worker.engine.workflow;

public record StartAgentWorkflowRequest(
        String workflowRunId,
        String ticketId,
        String rawSpecification,
        int version
) {
}
