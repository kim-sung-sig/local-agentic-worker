package com.example.worker.engine.domain.model;

import java.time.Instant;

public record AttemptRecord(
        int attemptNumber,
        String implementationArtifactRef,
        String qaReportRef,
        Integer qaScore,
        AttemptStatus status,
        Instant createdAt,
        Instant finishedAt
) {
}
