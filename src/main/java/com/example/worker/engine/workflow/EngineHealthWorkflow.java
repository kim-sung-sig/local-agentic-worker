package com.example.worker.engine.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EngineHealthWorkflow {

    @WorkflowMethod
    String run();
}
