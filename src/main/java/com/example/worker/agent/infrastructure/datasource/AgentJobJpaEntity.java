package com.example.worker.agent.infrastructure.datasource;

import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentJobStatus;
import com.example.worker.agent.domain.model.AgentPhase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_job")
class AgentJobJpaEntity {

    @Id
    private UUID id;

    @Column(name = "issue_id", nullable = false)
    private UUID issueId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentJobStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "document_path")
    private String documentPath;

    @Column(name = "claude_session_id")
    private String claudeSessionId;

    protected AgentJobJpaEntity() {}

    static AgentJobJpaEntity from(AgentJob job) {
        AgentJobJpaEntity e = new AgentJobJpaEntity();
        e.id = job.getId().value();
        e.issueId = job.getIssueId();
        e.projectId = job.getProjectId();
        e.branchName = job.getBranchName();
        e.phase = job.getPhase();
        e.status = job.getStatus();
        e.startedAt = job.getStartedAt();
        e.finishedAt = job.getFinishedAt();
        e.errorMessage = job.getErrorMessage();
        e.prUrl = job.getPrUrl();
        e.documentPath = job.getDocumentPath();
        e.claudeSessionId = job.getClaudeSessionId();
        return e;
    }

    AgentJob toDomain() {
        return AgentJob.reconstitute(
                AgentJobId.of(id), issueId, projectId,
                branchName, phase, status, startedAt, finishedAt,
                errorMessage, prUrl, documentPath, claudeSessionId);
    }
}
