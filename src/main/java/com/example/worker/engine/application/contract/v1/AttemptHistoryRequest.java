package com.example.worker.engine.application.contract.v1;

public record AttemptHistoryRequest(
        ActivityRequestMetadata metadata,
        ArtifactRef implementationArtifactRef,
        ArtifactRef qaReportRef,
        Integer qaScore,
        String status,
        int version
) {
}
