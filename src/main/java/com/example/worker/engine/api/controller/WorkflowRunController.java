package com.example.worker.engine.api.controller;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.engine.api.request.StageDecisionRequest;
import com.example.worker.engine.api.request.StartWorkflowRequest;
import com.example.worker.engine.api.response.AttemptResponse;
import com.example.worker.engine.api.response.WorkflowRunResponse;
import com.example.worker.engine.application.port.AttemptRecordRepository;
import com.example.worker.engine.application.service.AgentWorkerStarter;
import com.example.worker.engine.domain.model.WorkflowRunId;
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import com.example.worker.engine.workflow.AgentWorkerWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/engine/workflow-runs")
public class WorkflowRunController {

    private final AgentWorkerStarter starter;
    private final WorkflowClient workflowClient;
    private final AttemptRecordRepository attemptRecordRepository;

    public WorkflowRunController(AgentWorkerStarter starter, WorkflowClient workflowClient,
                                  AttemptRecordRepository attemptRecordRepository) {
        this.starter = starter;
        this.workflowClient = workflowClient;
        this.attemptRecordRepository = attemptRecordRepository;
    }

    @PostMapping
    public ResponseEntity<WorkflowRunResponse> start(@Valid @RequestBody StartWorkflowRequest request) {
        String workflowRunId = UUID.randomUUID().toString();
        starter.start(workflowRunId, request.ticketId(), request.rawSpecification());
        return ResponseEntity.accepted().body(new WorkflowRunResponse(
                workflowRunId, WorkflowStage.INTAKE.name(), WorkflowRunStatus.RUNNING.name()));
    }

    @GetMapping("/{workflowRunId}")
    public WorkflowRunResponse get(@PathVariable String workflowRunId) {
        AgentWorkerWorkflow stub = stub(workflowRunId);
        try {
            return new WorkflowRunResponse(workflowRunId, stub.currentStage().name(), stub.status().name());
        } catch (WorkflowNotFoundException e) {
            throw new BusinessException(ErrorCode.WORKFLOW_RUN_NOT_FOUND);
        }
    }

    @GetMapping("/{workflowRunId}/attempts")
    public List<AttemptResponse> attempts(@PathVariable String workflowRunId) {
        WorkflowRunId id = WorkflowRunId.of(UUID.fromString(workflowRunId));
        return attemptRecordRepository.findByWorkflowRunId(id).stream()
                .map(AttemptResponse::from)
                .toList();
    }

    @PostMapping("/{workflowRunId}/decisions")
    public ResponseEntity<Void> decide(@PathVariable String workflowRunId,
                                        @Valid @RequestBody StageDecisionRequest request) {
        validate(request);
        AgentWorkerWorkflow stub = stub(workflowRunId);
        switch (request.decision()) {
            case APPROVE -> stub.approve();
            case REJECT -> stub.reject(request.reason(), request.targetStage());
            case REQUEST_REVISION -> stub.requestRevision(request.reason());
            case RETRY -> stub.retryStage();
            case CANCEL -> stub.cancel();
        }
        return ResponseEntity.accepted().build();
    }

    private void validate(StageDecisionRequest request) {
        if (request.decision() == StageDecisionRequest.StageDecisionType.REJECT
                && request.targetStage() == null) {
            throw new BusinessException(ErrorCode.INVALID_STAGE_DECISION);
        }
        if (request.decision() == StageDecisionRequest.StageDecisionType.REQUEST_REVISION
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_STAGE_DECISION);
        }
    }

    private AgentWorkerWorkflow stub(String workflowRunId) {
        return workflowClient.newWorkflowStub(AgentWorkerWorkflow.class, workflowRunId);
    }
}
