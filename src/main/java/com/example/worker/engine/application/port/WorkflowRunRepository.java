package com.example.worker.engine.application.port;

import com.example.worker.engine.domain.model.WorkflowRun;
import com.example.worker.engine.domain.model.WorkflowRunId;

import java.util.Optional;

public interface WorkflowRunRepository {

    WorkflowRun save(WorkflowRun workflowRun);

    Optional<WorkflowRun> findById(WorkflowRunId id);

    Optional<WorkflowRun> findByTemporalWorkflowId(String temporalWorkflowId);
}
