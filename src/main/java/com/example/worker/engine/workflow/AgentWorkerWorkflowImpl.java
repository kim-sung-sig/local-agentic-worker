package com.example.worker.engine.workflow;

import com.example.worker.engine.application.contract.v1.ActivityRequestMetadata;
import com.example.worker.engine.application.contract.v1.AttemptHistoryRequest;
import com.example.worker.engine.application.contract.v1.AttemptPolicy;
import com.example.worker.engine.application.contract.v1.ImplementationRequest;
import com.example.worker.engine.application.contract.v1.ImplementationResponse;
import com.example.worker.engine.application.contract.v1.NotificationRequest;
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
    private StartAgentWorkflowRequest runRequest;

    @Override
    public String run(StartAgentWorkflowRequest request) {
        runRequest = request;
        try {
            notify(request, "WORKFLOW_CREATED", "INFO", "워크플로가 생성되었습니다", "워크플로가 시작되었습니다.");
            notify(request, "ACTIVITY_STARTED", "INFO", "INTAKE 작업 시작", "티켓 분석을 시작합니다.");
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
        } catch (RuntimeException exception) {
            try {
                notify(request, "ACTIVITY_FAILED", "ERROR", "워크플로 Activity 실패", exception.getMessage());
            } catch (RuntimeException ignored) {
                // The original activity failure is the workflow outcome.
            }
            throw exception;
        }
    }

    private void handleIntake(StartAgentWorkflowRequest request) {
        assessment = activities.assessTicket(new TicketAssessmentRequest(
                metadata(request, WorkflowStage.INTAKE), request.ticketId(), request.rawSpecification(), 1));
        notify(request, "ACTIVITY_COMPLETED", "INFO", "INTAKE 작업 완료", "티켓 분석이 완료되었습니다.");
        if (awaitGate(WorkflowStage.INTAKE)) {
            transitionTo(WorkflowStage.PLANNING);
        }
    }

    private void handlePlanning(StartAgentWorkflowRequest request) {
        notify(request, "ACTIVITY_STARTED", "INFO", "PLANNING 작업 시작", "구현 계획 생성을 시작합니다.");
        planning = activities.planImplementation(new PlanningRequest(
                metadata(request, WorkflowStage.PLANNING), assessment.refinedSpecification(), 1));
        notify(request, "ACTIVITY_COMPLETED", "INFO", "PLANNING 작업 완료", "구현 계획이 생성되었습니다.");
        if (awaitGate(WorkflowStage.PLANNING)) {
            transitionTo(WorkflowStage.WORKSPACE);
        }
    }

    private void handleWorkspace(StartAgentWorkflowRequest request) {
        notify(request, "ACTIVITY_STARTED", "INFO", "WORKSPACE 작업 시작", "작업 공간 준비를 시작합니다.");
        workspace = activities.prepareWorkspace(new WorkspaceRequest(
                metadata(request, WorkflowStage.WORKSPACE), assessment.recommendedChangeType(),
                request.ticketId(), 1));
        notify(request, "ACTIVITY_COMPLETED", "INFO", "WORKSPACE 작업 완료", "작업 공간이 준비되었습니다.");
        transitionTo(WorkflowStage.IMPLEMENTATION);
    }

    private void handleImplementation(StartAgentWorkflowRequest request) {
        notify(request, "ACTIVITY_STARTED", "INFO", "IMPLEMENTATION 작업 시작", "구현 작업을 시작합니다.");
        WorkspaceRef workspaceRef = workspace.workspaceRef();
        implementation = activities.implement(new ImplementationRequest(
                metadata(request, WorkflowStage.IMPLEMENTATION), workspaceRef,
                planning.implementationPlanRef(), 1));
        notify(request, "ACTIVITY_COMPLETED", "INFO", "IMPLEMENTATION 작업 완료", "구현 작업이 완료되었습니다.");
        transitionTo(WorkflowStage.QA);
    }

    private void handleQa(StartAgentWorkflowRequest request) {
        if (attemptPolicy == null) {
            attemptPolicy = attemptPolicyResolver.resolve(planning.attemptPolicy());
        }

        notify(request, "ACTIVITY_STARTED", "INFO", "QA 작업 시작", "품질 검증을 시작합니다.");
        QaResult qaResult = activities.runQualityAssurance(new QaRequest(
                metadata(request, WorkflowStage.QA), workspace.workspaceRef(),
                implementation.implementationArtifactRef(), 1));
        notify(request, "ACTIVITY_COMPLETED", qaResult.passed() ? "INFO" : "WARNING", "QA 작업 완료", "품질 검증이 완료되었습니다.");

        notify(request, "ACTIVITY_STARTED", "INFO", "QA 시도 기록 시작", "QA 시도 기록을 시작합니다.");
        activities.recordAttemptHistory(new AttemptHistoryRequest(
                metadata(request, WorkflowStage.QA), implementation.implementationArtifactRef(),
                qaResult.reportRef(), qaResult.score(), qaResult.passed() ? "PASSED" : "FAILED", 1));
        notify(request, "ATTEMPT_CHANGED", qaResult.passed() ? "INFO" : "WARNING", "QA 시도 기록", "QA 시도 결과가 기록되었습니다.");

        boolean thresholdMet = qaResult.score() >= attemptPolicy.minimumQaScore();
        boolean attemptsRemain = attemptNumber < attemptPolicy.maxAttempts();

        if (!thresholdMet && attemptsRemain) {
            // Policy-driven auto-retry — no human gate involved while attempts remain.
            attemptNumber++;
            transitionTo(WorkflowStage.IMPLEMENTATION);
            return;
        }

        if (!thresholdMet) {
            // Attempts exhausted without meeting the threshold. Spec Acceptance Criteria 5
            // ("기준을 충족한 결과만 Draft PR을 만들 수 있고") permits no human override here —
            // approving a failing result must never reach REVIEW_MERGE, so the gate is never
            // offered; the run terminates as FAILED instead.
            changeStatus(WorkflowRunStatus.FAILED);
            currentStage = null;
            return;
        }

        if (awaitGate(WorkflowStage.QA)) {
            transitionTo(WorkflowStage.REVIEW_MERGE);
        } else if (status == WorkflowRunStatus.RUNNING) {
            // Threshold was already met, but a human explicitly rejected a passing result
            // (e.g. wants a different approach) — this manual path from T04 remains available.
            attemptNumber++;
        }
    }

    private void handleReviewMerge(StartAgentWorkflowRequest request) {
        notify(request, "ACTIVITY_STARTED", "INFO", "Draft PR 생성 시작", "Draft PR 생성을 시작합니다.");
        SourceControlResponse draftPr = activities.manageSourceControl(new SourceControlRequest(
                metadata(request, WorkflowStage.REVIEW_MERGE), workspace.workspaceRef(), "CREATE_DRAFT_PR", 1));
        notify(request, "ACTIVITY_COMPLETED", "INFO", "Draft PR 생성 완료", "Draft PR이 생성되었습니다.");

        if (!awaitGate(WorkflowStage.REVIEW_MERGE)) {
            return;
        }

        notify(request, "ACTIVITY_STARTED", "INFO", "PR 병합 시작", "PR 병합을 시작합니다.");
        activities.manageSourceControl(new SourceControlRequest(
                metadata(request, WorkflowStage.REVIEW_MERGE), workspace.workspaceRef(), "MERGE", 1));
        notify(request, "ACTIVITY_COMPLETED", "INFO", "PR 병합 완료", "PR이 병합되었습니다.");

        changeStatus(WorkflowRunStatus.COMPLETED);
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
            decision("CANCEL", stage);
            changeStatus(WorkflowRunStatus.CANCELLED);
            currentStage = null;
            return false;
        }

        if (rejectionTarget != null) {
            approveSignaled = false;
            gateHistory.add(new StageGate(stage, lastDecision, lastReason, epochMillisToInstant()));
            decision(lastDecision.name(), stage);
            WorkflowStage target = rejectionTarget;
            rejectionTarget = null;
            changeStatus(WorkflowRunStatus.PAUSED);

            Workflow.await(() -> retryStageSignaled || cancelSignaled);

            if (cancelSignaled) {
                decision("CANCEL", stage);
                changeStatus(WorkflowRunStatus.CANCELLED);
                currentStage = null;
                return false;
            }

            retryStageSignaled = false;
            changeStatus(WorkflowRunStatus.RUNNING);
            decision("RETRY", target);
            transitionTo(target);
            return false;
        }

        approveSignaled = false;
        gateHistory.add(new StageGate(stage, GateDecision.APPROVE, null, epochMillisToInstant()));
        decision("APPROVE", stage);
        return true;
    }

    private Instant epochMillisToInstant() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }

    private ActivityRequestMetadata metadata(StartAgentWorkflowRequest request, WorkflowStage stage) {
        return new ActivityRequestMetadata(request.workflowRunId(), stage, attemptNumber, 1);
    }

    private void notify(StartAgentWorkflowRequest request, String type, String severity, String title, String message) {
        activities.sendNotification(new NotificationRequest(metadata(request, currentStage), request.ticketId(), type, severity, title, message, 1));
    }

    private void transitionTo(WorkflowStage next) {
        notify(runRequest, "STAGE_CHANGED", "INFO", "단계 변경", currentStage + " → " + next);
        currentStage = next;
    }

    private void changeStatus(WorkflowRunStatus next) {
        status = next;
        notify(runRequest, "WORKFLOW_STATUS_CHANGED", next == WorkflowRunStatus.FAILED ? "ERROR" : "INFO", "워크플로 상태 변경", next.name());
    }

    private void decision(String decision, WorkflowStage stage) {
        notify(runRequest, "DECISION_RECORDED", "INFO", "게이트 결정", stage + ": " + decision);
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
