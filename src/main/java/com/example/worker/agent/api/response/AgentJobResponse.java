package com.example.worker.agent.api.response;

import com.example.worker.agent.domain.model.AgentJob;

import java.time.Instant;
import java.util.UUID;

public record AgentJobResponse(
        UUID id,
        String status,
        String branchName,
        String prUrl,
        Instant startedAt,
        Instant finishedAt
) {
    public static AgentJobResponse from(AgentJob job) {
        return new AgentJobResponse(
                job.getId().value(),
                job.getStatus().name(),
                job.getBranchName(),
                job.getPrUrl(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
