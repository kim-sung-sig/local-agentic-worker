package com.example.worker.engine.application.contract.v1;

public record TicketAssessmentRequest(
        ActivityRequestMetadata metadata,
        String ticketId,
        String rawSpecification,
        int version
) {
}
