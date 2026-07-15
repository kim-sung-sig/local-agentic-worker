package com.example.worker.engine.domain.model;

import java.time.Instant;

public record StageGate(WorkflowStage stage, GateDecision decision, String reason, Instant decidedAt) {
}
