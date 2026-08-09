package com.example.worker.engine.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StartWorkflowRequest(
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a UUID")
        String ticketId,
        @NotBlank String rawSpecification
) {
}
