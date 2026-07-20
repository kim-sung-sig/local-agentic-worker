package com.example.worker.engine.infrastructure.activity;

import com.example.worker.contracts.agentworker.EngineNotificationRequested;
import com.example.worker.engine.application.contract.v1.ArtifactRef;
import com.example.worker.engine.application.contract.v1.AttemptHistoryRequest;
import com.example.worker.engine.application.contract.v1.AttemptHistoryResponse;
import com.example.worker.engine.application.contract.v1.AttemptPolicy;
import com.example.worker.engine.application.contract.v1.ImplementationRequest;
import com.example.worker.engine.application.contract.v1.ImplementationResponse;
import com.example.worker.engine.application.contract.v1.NotificationRequest;
import com.example.worker.engine.application.contract.v1.NotificationResponse;
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
import com.example.worker.engine.application.port.NotificationPublisher;
import com.example.worker.engine.application.port.WorkflowRunRepository;
import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.AttemptStatus;
import com.example.worker.engine.domain.model.WorkflowRun;
import com.example.worker.engine.workflow.EngineActivities;
import com.example.worker.runtime.application.WorkspaceRuntime;
import com.example.worker.scm.application.SourceControlPlugin;
import com.example.worker.scm.application.SourceControlPlugin.CreateDraftPullRequestCommand;
import com.example.worker.scm.application.SourceControlPlugin.MergePullRequestCommand;
import com.example.worker.scm.application.SourceControlPlugin.PullRequestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Reference wiring of {@link EngineActivities} connecting T02 (persistence), T05 (workspace
 * runtime) and T07 (source control) into one Activity implementation. {@code assessTicket},
 * {@code planImplementation}, {@code implement}, and {@code runQualityAssurance} are
 * deterministic stand-ins only — real AI/QA judgement is a separate, not-yet-built Agent Adapter
 * concern. The base branch ("main") is likewise a placeholder here: {@link WorkspaceRuntime} and
 * {@link SourceControlPlugin} themselves accept any caller-supplied branch (verified in T05/T07),
 * so a real Adapter only needs to pass the actual project base branch.
 */
@Component
public class EngineActivitiesImpl implements EngineActivities {

    private static final Logger log = LoggerFactory.getLogger(EngineActivitiesImpl.class);
    private static final String REFERENCE_BASE_BRANCH = "main";

    private final WorkspaceRuntime workspaceRuntime;
    private final SourceControlPlugin sourceControlPlugin;
    private final WorkflowRunRepository workflowRunRepository;
    private final NotificationPublisher notificationPublisher;

    public EngineActivitiesImpl(WorkspaceRuntime workspaceRuntime,
                                 SourceControlPlugin sourceControlPlugin,
                                 WorkflowRunRepository workflowRunRepository,
                                 NotificationPublisher notificationPublisher) {
        this.workspaceRuntime = workspaceRuntime;
        this.sourceControlPlugin = sourceControlPlugin;
        this.workflowRunRepository = workflowRunRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public TicketAssessmentResponse assessTicket(TicketAssessmentRequest request) {
        log.info("[Activity] assessTicket workflowRunId={}", request.metadata().workflowRunId());
        ensureWorkflowRunExists(request.metadata().workflowRunId(), request.ticketId());
        return new TicketAssessmentResponse(request.rawSpecification(), "FEATURE", 1);
    }

    // P1-3: persist the WorkflowRun row from INTAKE (the first Activity of every run) rather than
    // lazily at the first QA attempt, so a run that fails/cancels before reaching QA still leaves
    // a durable record. Looked up by temporalWorkflowId (the external correlation key), not by
    // WorkflowRunId (the aggregate's own surrogate key, assigned fresh by WorkflowRun.create) —
    // skipped when a row already exists (e.g. Activity retry after a task failure).
    private WorkflowRun ensureWorkflowRunExists(String workflowRunId, String ticketId) {
        return workflowRunRepository.findByTemporalWorkflowId(workflowRunId)
                .orElseGet(() -> workflowRunRepository.save(WorkflowRun.create(UUID.fromString(ticketId), workflowRunId)));
    }

    @Override
    public PlanningResponse planImplementation(PlanningRequest request) {
        ArtifactRef planRef = artifactRef(request.metadata().workflowRunId(), "plan", "IMPLEMENTATION_PLAN");
        return new PlanningResponse(planRef, new AttemptPolicy(0, 0, 1), 1);
    }

    @Override
    public WorkspaceResponse prepareWorkspace(WorkspaceRequest request) {
        String workflowRunId = request.metadata().workflowRunId();
        String branchName = branchNameFor(workflowRunId);
        WorkspaceRuntime.Workspace workspace = workspaceRuntime.acquire(workflowRunId, branchName, REFERENCE_BASE_BRANCH);
        return new WorkspaceResponse(new WorkspaceRef(workspace.path(), 1), workspace.branchName(), 1);
    }

    @Override
    public ImplementationResponse implement(ImplementationRequest request) {
        ArtifactRef implRef = artifactRef(request.metadata().workflowRunId(),
                "impl-" + request.metadata().attemptNumber(), "IMPLEMENTATION_SUMMARY");
        return new ImplementationResponse(implRef, 1);
    }

    @Override
    public QaResult runQualityAssurance(QaRequest request) {
        ArtifactRef reportRef = artifactRef(request.metadata().workflowRunId(),
                "qa-report-" + request.metadata().attemptNumber(), "QA_REPORT");
        return new QaResult(true, 95, reportRef, 1);
    }

    @Override
    public AttemptHistoryResponse recordAttemptHistory(AttemptHistoryRequest request) {
        String workflowRunId = request.metadata().workflowRunId();
        WorkflowRun run = workflowRunRepository.findByTemporalWorkflowId(workflowRunId)
                .orElseThrow(() -> new IllegalStateException("WorkflowRun not found: " + workflowRunId));

        Instant now = Instant.now();
        run.recordAttempt(new AttemptRecord(
                request.metadata().attemptNumber(),
                request.implementationArtifactRef() != null ? request.implementationArtifactRef().value() : null,
                request.qaReportRef() != null ? request.qaReportRef().value() : null,
                request.qaScore(),
                AttemptStatus.valueOf(request.status()),
                now, now));

        workflowRunRepository.save(run);
        return new AttemptHistoryResponse(true, 1);
    }

    @Override
    public SourceControlResponse manageSourceControl(SourceControlRequest request) {
        String workflowRunId = request.metadata().workflowRunId();
        String workspacePath = request.workspaceRef().value();
        String branchName = branchNameFor(workflowRunId);
        String idempotencyKey = request.metadata().idempotencyKey();

        PullRequestResult result = switch (request.action()) {
            case "CREATE_DRAFT_PR" -> sourceControlPlugin.createDraftPullRequest(new CreateDraftPullRequestCommand(
                    idempotencyKey, workspacePath, REFERENCE_BASE_BRANCH, branchName,
                    "Agent Worker Engine run " + workflowRunId,
                    "Automatically generated by Agent Worker Engine.", true));
            case "MERGE" -> sourceControlPlugin.mergePullRequest(
                    new MergePullRequestCommand(idempotencyKey, workspacePath, branchName));
            default -> throw new IllegalArgumentException("Unknown source control action: " + request.action());
        };

        return new SourceControlResponse(result.url(), result.status(), 1);
    }

    // Stage 2 (app-boundary): Engine never resolves Issue/Notification state itself — it only knows
    // its own WorkflowRun aggregate and the ticketId supplied by the caller. Publishing this Kafka
    // message (rather than calling IssueRepository/NotificationCommandService in-process) is what
    // lets Control Plane and Agent Engine become separate deployables sharing only `contracts`.
    @Override
    public NotificationResponse sendNotification(NotificationRequest request) {
        WorkflowRun run = workflowRunRepository.findByTemporalWorkflowId(request.metadata().workflowRunId())
                .orElseGet(() -> ensureWorkflowRunExists(request.metadata().workflowRunId(), request.ticketId()));
        String idempotencyKey = request.metadata().idempotencyKey() + ":" + request.type() + ":" + request.title()
                + ":" + request.message();
        notificationPublisher.publish(new EngineNotificationRequested(
                run.getId().value().toString(), run.getTicketId().toString(),
                request.type(), request.severity(), request.title(), request.message(),
                idempotencyKey, Instant.now()));
        log.info("[Notification] type={} message={}", request.type(), request.message());
        return new NotificationResponse(true, 1);
    }

    private static String branchNameFor(String workflowRunId) {
        return "feature/" + workflowRunId;
    }

    private static ArtifactRef artifactRef(String workflowRunId, String kind, String label) {
        return new ArtifactRef("artifact://" + workflowRunId + "/" + kind, label, 1);
    }
}
