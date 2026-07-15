package com.example.worker.engine.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
public class WorkflowRun {

    private static final Map<WorkflowStage, Set<WorkflowStage>> VALID_TRANSITIONS = new EnumMap<>(WorkflowStage.class);

    static {
        VALID_TRANSITIONS.put(WorkflowStage.INTAKE, EnumSet.of(WorkflowStage.PLANNING));
        VALID_TRANSITIONS.put(WorkflowStage.PLANNING, EnumSet.of(WorkflowStage.WORKSPACE));
        VALID_TRANSITIONS.put(WorkflowStage.WORKSPACE, EnumSet.of(WorkflowStage.IMPLEMENTATION));
        VALID_TRANSITIONS.put(WorkflowStage.IMPLEMENTATION, EnumSet.of(WorkflowStage.QA));
        VALID_TRANSITIONS.put(WorkflowStage.QA, EnumSet.of(WorkflowStage.IMPLEMENTATION, WorkflowStage.REVIEW_MERGE));
        VALID_TRANSITIONS.put(WorkflowStage.REVIEW_MERGE, EnumSet.noneOf(WorkflowStage.class));
    }

    private final WorkflowRunId id;
    private final UUID ticketId;
    private final String temporalWorkflowId;
    private WorkflowStage currentStage;
    private WorkflowRunStatus status;
    private String workspaceRef;
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<StageGate> gates;
    private final List<AttemptRecord> attempts;

    private WorkflowRun(WorkflowRunId id, UUID ticketId, String temporalWorkflowId,
                        WorkflowStage currentStage, WorkflowRunStatus status, String workspaceRef,
                        Instant startedAt, Instant finishedAt,
                        List<StageGate> gates, List<AttemptRecord> attempts) {
        this.id = id;
        this.ticketId = ticketId;
        this.temporalWorkflowId = temporalWorkflowId;
        this.currentStage = currentStage;
        this.status = status;
        this.workspaceRef = workspaceRef;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.gates = new ArrayList<>(gates);
        this.attempts = new ArrayList<>(attempts);
    }

    public static WorkflowRun create(UUID ticketId, String temporalWorkflowId) {
        return new WorkflowRun(WorkflowRunId.newId(), ticketId, temporalWorkflowId,
                WorkflowStage.INTAKE, WorkflowRunStatus.RUNNING, null,
                Instant.now(), null, List.of(), List.of());
    }

    public static WorkflowRun reconstitute(WorkflowRunId id, UUID ticketId, String temporalWorkflowId,
                                           WorkflowStage currentStage, WorkflowRunStatus status, String workspaceRef,
                                           Instant startedAt, Instant finishedAt,
                                           List<StageGate> gates, List<AttemptRecord> attempts) {
        return new WorkflowRun(id, ticketId, temporalWorkflowId, currentStage, status, workspaceRef,
                startedAt, finishedAt, gates, attempts);
    }

    public void advanceTo(WorkflowStage next) {
        if (!VALID_TRANSITIONS.getOrDefault(currentStage, Set.of()).contains(next)) {
            throw new IllegalStateException(
                    "Invalid stage transition: " + currentStage + " -> " + next);
        }
        this.currentStage = next;
    }

    public void assignWorkspaceRef(String ref) {
        if (this.workspaceRef != null) {
            throw new IllegalStateException("WorkspaceRef already assigned");
        }
        this.workspaceRef = ref;
    }

    public void recordGateDecision(StageGate gate) {
        this.gates.add(gate);
    }

    public void recordAttempt(AttemptRecord attempt) {
        int expectedNumber = attempts.size() + 1;
        if (attempt.attemptNumber() != expectedNumber) {
            throw new IllegalArgumentException(
                    "Attempt number must be " + expectedNumber + " but was " + attempt.attemptNumber());
        }
        this.attempts.add(attempt);
    }

    public List<StageGate> getGates() {
        return List.copyOf(gates);
    }

    public List<AttemptRecord> getAttempts() {
        return List.copyOf(attempts);
    }
}
