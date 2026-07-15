package com.example.worker.engine.application.contract.v1;

public record TicketAssessmentResponse(
        String refinedSpecification,
        String recommendedChangeType,
        int version
) {
}
