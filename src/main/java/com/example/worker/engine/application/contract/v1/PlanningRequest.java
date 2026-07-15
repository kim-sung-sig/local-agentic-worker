package com.example.worker.engine.application.contract.v1;

public record PlanningRequest(
        ActivityRequestMetadata metadata,
        String refinedSpecification,
        int version
) {
}
