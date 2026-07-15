package com.example.worker.engine.api.request;

import jakarta.validation.constraints.NotBlank;

public record StartWorkflowRequest(
        @NotBlank String ticketId,
        @NotBlank String rawSpecification
) {
}
