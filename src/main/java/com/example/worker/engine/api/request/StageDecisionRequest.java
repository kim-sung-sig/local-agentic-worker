package com.example.worker.engine.api.request;

import com.example.worker.engine.domain.model.WorkflowStage;
import jakarta.validation.constraints.NotNull;

public record StageDecisionRequest(
        @NotNull StageDecisionType decision,
        String reason,
        WorkflowStage targetStage
) {
    public enum StageDecisionType { APPROVE, REJECT, REQUEST_REVISION, RETRY, CANCEL }
}
