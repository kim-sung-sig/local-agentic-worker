package com.example.worker.engine.application.port;

import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.WorkflowRunId;

import java.util.List;

public interface AttemptRecordRepository {

    List<AttemptRecord> findByWorkflowRunId(WorkflowRunId workflowRunId);
}
