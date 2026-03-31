package com.example.worker.agent.api.response;

import com.example.worker.agent.domain.model.AgentJob;

import java.time.Instant;
import java.util.UUID;

public record AgentJobResponse(
        UUID id,
        String phase,
        String status,
        String branchName,
        String prUrl,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
    public static AgentJobResponse from(AgentJob job) {
        return new AgentJobResponse(
                job.getId().value(),
                job.getPhase().name(),
                job.getStatus().name(),
                job.getBranchName(),
                job.getPrUrl(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
