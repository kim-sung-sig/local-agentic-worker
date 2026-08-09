package com.example.worker.engine.workflow;

import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentWorkerWorkflow {

    @WorkflowMethod(name = "run")
    String run(StartAgentWorkflowRequest request);

    @SignalMethod
    void approve();

    @SignalMethod
    void reject(String reason, WorkflowStage targetStage);

    @SignalMethod
    void requestRevision(String reason);

    @SignalMethod
    void retryStage();

    @SignalMethod
    void cancel();

    @QueryMethod
    WorkflowStage currentStage();

    @QueryMethod
    WorkflowRunStatus status();
}
