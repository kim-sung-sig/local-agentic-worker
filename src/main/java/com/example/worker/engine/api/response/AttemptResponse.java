package com.example.worker.engine.api.response;

import com.example.worker.engine.domain.model.AttemptRecord;

import java.time.Instant;

public record AttemptResponse(
        int attemptNumber,
        String implementationArtifactRef,
        String qaReportRef,
        Integer qaScore,
        String status,
        Instant createdAt,
        Instant finishedAt
) {
    public static AttemptResponse from(AttemptRecord record) {
        return new AttemptResponse(
                record.attemptNumber(),
                record.implementationArtifactRef(),
                record.qaReportRef(),
                record.qaScore(),
                record.status().name(),
                record.createdAt(),
                record.finishedAt());
    }
}
