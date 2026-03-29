package com.example.worker.agent.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class AgentJob {

    private final AgentJobId id;
    private final UUID issueId;
    private final UUID projectId;
    private final String branchName;
    private AgentJobStatus status;
    private final Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
    private String prUrl;

    private AgentJob(AgentJobId id, UUID issueId, UUID projectId, String branchName,
                     AgentJobStatus status, Instant startedAt,
                     Instant finishedAt, String errorMessage, String prUrl) {
        this.id = id;
        this.issueId = issueId;
        this.projectId = projectId;
        this.branchName = branchName;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
        this.prUrl = prUrl;
    }

    public static AgentJob create(UUID issueId, UUID projectId, String branchName) {
        return new AgentJob(AgentJobId.newId(), issueId, projectId, branchName,
                AgentJobStatus.PENDING, Instant.now(), null, null, null);
    }

    public static AgentJob reconstitute(AgentJobId id, UUID issueId, UUID projectId,
                                        String branchName, AgentJobStatus status,
                                        Instant startedAt, Instant finishedAt,
                                        String errorMessage, String prUrl) {
        return new AgentJob(id, issueId, projectId, branchName, status,
                startedAt, finishedAt, errorMessage, prUrl);
    }

    public void start() {
        this.status = AgentJobStatus.PLANNING;
    }

    public void startPlanning() {
        this.status = AgentJobStatus.PLANNING;
    }

    public void startCoding() {
        this.status = AgentJobStatus.CODING;
    }

    public void startVerifying() {
        this.status = AgentJobStatus.VERIFYING;
    }

    public void complete(String prUrl) {
        this.status = AgentJobStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.prUrl = prUrl;
    }

    public void fail(String errorMessage) {
        this.status = AgentJobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

}
