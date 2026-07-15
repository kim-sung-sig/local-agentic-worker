package com.example.worker.engine.application.service;

import com.example.worker.engine.workflow.AgentWorkerWorkflow;
import com.example.worker.engine.workflow.StartAgentWorkflowRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkerStarter {

    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public AgentWorkerStarter(WorkflowClient workflowClient,
                              @Value("${agent.engine.temporal.task-queue}") String taskQueue) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    /**
     * Starts a new {@link AgentWorkerWorkflow} run and returns immediately —
     * the caller does not block on the workflow's eventual result.
     */
    public void start(String workflowRunId, String ticketId, String rawSpecification) {
        AgentWorkerWorkflow workflow = workflowClient.newWorkflowStub(
                AgentWorkerWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId(workflowRunId)
                        .build());

        WorkflowClient.start(workflow::run,
                new StartAgentWorkflowRequest(workflowRunId, ticketId, rawSpecification, 1));
    }
}
