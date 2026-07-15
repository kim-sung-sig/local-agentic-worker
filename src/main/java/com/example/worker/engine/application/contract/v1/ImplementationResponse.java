package com.example.worker.engine.application.contract.v1;

public record ImplementationResponse(
        ArtifactRef implementationArtifactRef,
        int version
) {
}
