package com.example.worker.engine.application.contract.v1;

public record SourceControlResponse(
        String prUrl,
        String status,
        int version
) {
}
