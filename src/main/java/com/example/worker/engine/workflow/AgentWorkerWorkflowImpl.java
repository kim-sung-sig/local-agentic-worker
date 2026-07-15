package com.example.worker.engine.workflow;

import com.example.worker.engine.application.contract.v1.ActivityRequestMetadata;
import com.example.worker.engine.application.contract.v1.AttemptHistoryRequest;
import com.example.worker.engine.application.contract.v1.AttemptPolicy;
import com.example.worker.engine.application.contract.v1.ImplementationRequest;
import com.example.worker.engine.application.contract.v1.ImplementationResponse;
import com.example.worker.engine.application.contract.v1.PlanningRequest;
import com.example.worker.engine.application.contract.v1.PlanningResponse;
import com.example.worker.engine.application.contract.v1.QaRequest;
import com.example.worker.engine.application.contract.v1.QaResult;
import com.example.worker.engine.application.contract.v1.SourceControlRequest;
import com.example.worker.engine.application.contract.v1.SourceControlResponse;
import com.example.worker.engine.application.contract.v1.TicketAssessmentRequest;
import com.example.worker.engine.application.contract.v1.TicketAssessmentResponse;
import com.example.worker.engine.application.contract.v1.WorkspaceRef;
import com.example.worker.engine.application.contract.v1.WorkspaceRequest;
import com.example.worker.engine.application.contract.v1.WorkspaceResponse;
import com.example.worker.engine.application.service.AttemptPolicyResolver;
import com.example.worker.engine.domain.model.GateDecision;
import com.example.worker.engine.domain.model.StageGate;
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AgentWorkerWorkflowImpl implements AgentWorkerWorkflow {

    private final EngineActivities activities = Workflow.newActivityStub(
            EngineActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .build());

    private final List<StageGate> gateHistory = new ArrayList<>();
    private final AttemptPolicyResolver attemptPolicyResolver = new AttemptPolicyResolver();

    private WorkflowStage currentStage = WorkflowStage.INTAKE;
    private WorkflowRunStatus status = WorkflowRunStatus.RUNNING;

    private boolean approveSignaled;
    private boolean cancelSignaled;
    private boolean retryStageSignaled;
    private WorkflowStage rejectionTarget;
    private GateDecision lastDecision;
    private String lastReason;

    private TicketAssessmentResponse assessment;
    private PlanningResponse planning;
    private WorkspaceResponse workspace;
    private ImplementationResponse implementation;
    private AttemptPolicy attemptPolicy;
    private int attemptNumber = 1;

    @Override
    public String run(StartAgentWorkflowRequest request) {
        while (currentStage != null && status == WorkflowRunStatus.RUNNING) {
            switch (currentStage) {
                case INTAKE -> handleIntake(request);
                case PLANNING -> handlePlanning(request);
                case WORKSPACE -> handleWorkspace(request);
                case IMPLEMENTATION -> handleImplementation(request);
                case QA -> handleQa(request);
                case REVIEW_MERGE -> handleReviewMerge(request);
            }
        }
        return status.name();
    }

    private void handleIntake(StartAgentWorkflowRequest request) {
        assessment = activities.assessTicket(new TicketAssessmentRequest(
                metadata(request, WorkflowStage.INTAKE), request.ticketId(), request.rawSpecification(), 1));
        if (awaitGate(WorkflowStage.INTAKE)) {
            currentStage = WorkflowStage.PLANNING;
        }
    }

    private void handlePlanning(StartAgentWorkflowRequest request) {
        planning = activities.planImplementation(new PlanningRequest(
                metadata(request, WorkflowStage.PLANNING), assessment.refinedSpecification(), 1));
        if (awaitGate(WorkflowStage.PLANNING)) {
            currentStage = WorkflowStage.WORKSPACE;
        }
    }

    private void handleWorkspace(StartAgentWorkflowRequest request) {
        workspace = activities.prepareWorkspace(new WorkspaceRequest(
                metadata(request, WorkflowStage.WORKSPACE), assessment.recommendedChangeType(),
                request.ticketId(), 1));
        currentStage = WorkflowStage.IMPLEMENTATION;
    }

    private void handleImplementation(StartAgentWorkflowRequest request) {
        WorkspaceRef workspaceRef = workspace.workspaceRef();
        implementation = activities.implement(new ImplementationRequest(
                metadata(request, WorkflowStage.IMPLEMENTATION), workspaceRef,
                planning.implementationPlanRef(), 1));
        currentStage = WorkflowStage.QA;
    }

    private void handleQa(StartAgentWorkflowRequest request) {
        if (attemptPolicy == null) {
            attemptPolicy = attemptPolicyResolver.resolve(planning.attemptPolicy());
        }

        QaResult qaResult = activities.runQualityAssurance(new QaRequest(
                metadata(request, WorkflowStage.QA), workspace.workspaceRef(),
                implementation.implementationArtifactRef(), 1));

        activities.recordAttemptHistory(new AttemptHistoryRequest(
                metadata(request, WorkflowStage.QA), implementation.implementationArtifactRef(),
                qaResult.reportRef(), qaResult.score(), qaResult.passed() ? "PASSED" : "FAILED", 1));

        boolean thresholdMet = qaResult.score() >= attemptPolicy.minimumQaScore();
        boolean attemptsRemain = attemptNumber < attemptPolicy.maxAttempts();

        if (!thresholdMet && attemptsRemain) {
            // Policy-driven auto-retry — no human gate involved while attempts remain.
            attemptNumber++;
            currentStage = WorkflowStage.IMPLEMENTATION;
            return;
        }

        if (!thresholdMet) {
            // Attempts exhausted without meeting the threshold. Spec Acceptance Criteria 5
            // ("기준을 충족한 결과만 Draft PR을 만들 수 있고") permits no human override here —
            // approving a failing result must never reach REVIEW_MERGE, so the gate is never
            // offered; the run terminates as FAILED instead.
            status = WorkflowRunStatus.FAILED;
            currentStage = null;
            return;
        }

        if (awaitGate(WorkflowStage.QA)) {
            currentStage = WorkflowStage.REVIEW_MERGE;
        } else if (status == WorkflowRunStatus.RUNNING) {
            // Threshold was already met, but a human explicitly rejected a passing result
            // (e.g. wants a different approach) — this manual path from T04 remains available.
            attemptNumber++;
        }
    }

    private void handleReviewMerge(StartAgentWorkflowRequest request) {
        SourceControlResponse draftPr = activities.manageSourceControl(new SourceControlRequest(
                metadata(request, WorkflowStage.REVIEW_MERGE), workspace.workspaceRef(), "CREATE_DRAFT_PR", 1));

        if (!awaitGate(WorkflowStage.REVIEW_MERGE)) {
            return;
        }

        activities.manageSourceControl(new SourceControlRequest(
                metadata(request, WorkflowStage.REVIEW_MERGE), workspace.workspaceRef(), "MERGE", 1));

        status = WorkflowRunStatus.COMPLETED;
        currentStage = null;
    }

    /**
     * Blocks until approve/reject/requestRevision/cancel is signalled.
     * Returns true when the gate is approved and the caller should advance to the next stage.
     * Returns false when cancelled (status becomes CANCELLED) or when a rejection/revision was
     * signalled and later resumed via retryStage() (currentStage is repositioned to the target
     * stage and the dispatch loop re-enters from there).
     */
    private boolean awaitGate(WorkflowStage stage) {
        // Do not pre-clear signal flags before awaiting: an approve()/retryStage() signal can
        // legitimately arrive while this stage's Activity call is still in flight (i.e. before
        // this method is even entered). Only clear a flag once it has actually been consumed
        // below, never speculatively beforehand, or an early-arriving signal would be lost.
        Workflow.await(() -> approveSignaled || cancelSignaled || rejectionTarget != null);

        if (cancelSignaled) {
            status = WorkflowRunStatus.CANCELLED;
            currentStage = null;
            return false;
        }

        if (rejectionTarget != null) {
            approveSignaled = false;
            gateHistory.add(new StageGate(stage, lastDecision, lastReason, epochMillisToInstant()));
            WorkflowStage target = rejectionTarget;
            rejectionTarget = null;
            status = WorkflowRunStatus.PAUSED;

            Workflow.await(() -> retryStageSignaled || cancelSignaled);

            if (cancelSignaled) {
                status = WorkflowRunStatus.CANCELLED;
                currentStage = null;
                return false;
            }

            retryStageSignaled = false;
            status = WorkflowRunStatus.RUNNING;
            currentStage = target;
            return false;
        }

        approveSignaled = false;
        gateHistory.add(new StageGate(stage, GateDecision.APPROVE, null, epochMillisToInstant()));
        return true;
    }

    private Instant epochMillisToInstant() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }

    private ActivityRequestMetadata metadata(StartAgentWorkflowRequest request, WorkflowStage stage) {
        return new ActivityRequestMetadata(request.workflowRunId(), stage, attemptNumber, 1);
    }

    @Override
    public void approve() {
        this.approveSignaled = true;
    }

    @Override
    public void reject(String reason, WorkflowStage targetStage) {
        // Domain rule: a rejection can only roll the run back to the current stage or an
        // earlier one it has already passed through — never forward to a stage not yet
        // reached (e.g. rejecting at INTAKE straight to REVIEW_MERGE would skip Workspace/QA
        // entirely). Signal handlers must not throw (that would fail and retry the workflow
        // task indefinitely on replay), so an invalid target is silently ignored instead.
        if (targetStage == null || currentStage == null || targetStage.ordinal() > currentStage.ordinal()) {
            return;
        }
        this.lastDecision = GateDecision.REJECT;
        this.lastReason = reason;
        this.rejectionTarget = targetStage;
    }

    @Override
    public void requestRevision(String reason) {
        this.lastDecision = GateDecision.REQUEST_REVISION;
        this.lastReason = reason;
        this.rejectionTarget = currentStage;
    }

    @Override
    public void retryStage() {
        this.retryStageSignaled = true;
    }

    @Override
    public void cancel() {
        this.cancelSignaled = true;
    }

    @Override
    public WorkflowStage currentStage() {
        return currentStage;
    }

    @Override
    public WorkflowRunStatus status() {
        return status;
    }
}
