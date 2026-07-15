package com.example.worker.engine.application.contract.v1;

public record QaResult(boolean passed, int score, ArtifactRef reportRef, int version) {
}
