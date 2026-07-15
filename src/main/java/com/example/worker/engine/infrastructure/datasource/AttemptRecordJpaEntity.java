package com.example.worker.engine.infrastructure.datasource;

import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "engine_attempt_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_engine_attempt_run_number",
                columnNames = {"workflow_run_id", "attempt_number"}))
class AttemptRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_run_id", nullable = false)
    private UUID workflowRunId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "implementation_artifact_ref")
    private String implementationArtifactRef;

    @Column(name = "qa_report_ref")
    private String qaReportRef;

    @Column(name = "qa_score")
    private Integer qaScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AttemptRecordJpaEntity() {
    }

    static AttemptRecordJpaEntity from(UUID workflowRunId, AttemptRecord attempt) {
        AttemptRecordJpaEntity e = new AttemptRecordJpaEntity();
        e.id = UUID.randomUUID();
        e.workflowRunId = workflowRunId;
        e.attemptNumber = attempt.attemptNumber();
        e.implementationArtifactRef = attempt.implementationArtifactRef();
        e.qaReportRef = attempt.qaReportRef();
        e.qaScore = attempt.qaScore();
        e.status = attempt.status();
        e.createdAt = attempt.createdAt();
        e.finishedAt = attempt.finishedAt();
        return e;
    }

    AttemptRecord toDomain() {
        return new AttemptRecord(attemptNumber, implementationArtifactRef, qaReportRef,
                qaScore, status, createdAt, finishedAt);
    }
}
