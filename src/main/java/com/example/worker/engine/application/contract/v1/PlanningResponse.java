package com.example.worker.engine.application.contract.v1;

public record PlanningResponse(
        ArtifactRef implementationPlanRef,
        AttemptPolicy attemptPolicy,
        int version
) {
}
