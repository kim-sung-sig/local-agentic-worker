package com.example.worker.engine.infrastructure.datasource;

import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.GateDecision;
import com.example.worker.engine.domain.model.StageGate;
import com.example.worker.engine.domain.model.WorkflowRun;
import com.example.worker.engine.domain.model.WorkflowRunId;
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "engine_workflow_run")
class WorkflowRunJpaEntity {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "temporal_workflow_id", nullable = false, unique = true)
    private String temporalWorkflowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private WorkflowStage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowRunStatus status;

    @Column(name = "workspace_ref")
    private String workspaceRef;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @ElementCollection
    @CollectionTable(name = "engine_stage_gate", joinColumns = @JoinColumn(name = "workflow_run_id"))
    private List<StageGateEmbeddable> gates = new ArrayList<>();

    protected WorkflowRunJpaEntity() {
    }

    UUID getId() {
        return id;
    }

    static WorkflowRunJpaEntity from(WorkflowRun run) {
        WorkflowRunJpaEntity e = new WorkflowRunJpaEntity();
        e.id = run.getId().value();
        e.ticketId = run.getTicketId();
        e.temporalWorkflowId = run.getTemporalWorkflowId();
        e.currentStage = run.getCurrentStage();
        e.status = run.getStatus();
        e.workspaceRef = run.getWorkspaceRef();
        e.startedAt = run.getStartedAt();
        e.finishedAt = run.getFinishedAt();
        e.gates = run.getGates().stream()
                .map(StageGateEmbeddable::from)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return e;
    }

    WorkflowRun toDomain(List<AttemptRecord> attempts) {
        List<StageGate> domainGates = gates.stream().map(StageGateEmbeddable::toDomain).toList();
        return WorkflowRun.reconstitute(
                WorkflowRunId.of(id), ticketId, temporalWorkflowId, currentStage, status,
                workspaceRef, startedAt, finishedAt, domainGates, attempts);
    }

    @Embeddable
    static class StageGateEmbeddable {

        @Enumerated(EnumType.STRING)
        @Column(name = "stage")
        private WorkflowStage stage;

        @Enumerated(EnumType.STRING)
        @Column(name = "decision")
        private GateDecision decision;

        @Column(name = "reason")
        private String reason;

        @Column(name = "decided_at")
        private Instant decidedAt;

        protected StageGateEmbeddable() {
        }

        static StageGateEmbeddable from(StageGate gate) {
            StageGateEmbeddable e = new StageGateEmbeddable();
            e.stage = gate.stage();
            e.decision = gate.decision();
            e.reason = gate.reason();
            e.decidedAt = gate.decidedAt();
            return e;
        }

        StageGate toDomain() {
            return new StageGate(stage, decision, reason, decidedAt);
        }
    }
}
