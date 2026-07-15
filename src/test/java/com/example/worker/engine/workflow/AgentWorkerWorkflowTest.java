package com.example.worker.engine.workflow;

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
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.mockito.InOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AgentWorkerWorkflow")
class AgentWorkerWorkflowTest {

    private static final String TASK_QUEUE = "agent-worker-engine-test";

    private TestWorkflowEnvironment testEnvironment;
    private WorkflowClient workflowClient;
    private EngineActivities activities;

    @BeforeEach
    void setUp() {
        testEnvironment = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnvironment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(AgentWorkerWorkflowImpl.class);

        activities = mock(EngineActivities.class);
        stubDefaultActivities();
        // Mockito's proxy subclass copies @ActivityMethod onto its override, which Temporal's
        // activity registration rejects ("annotation can be used only on the interface method").
        // Delegate through a plain hand-written adapter so the registered class carries no
        // Temporal annotations of its own, while `activities` remains the mock for stub/verify.
        worker.registerActivitiesImplementations(new EngineActivitiesDelegate(activities));

        testEnvironment.start();
        workflowClient = testEnvironment.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnvironment.close();
    }

    private void stubDefaultActivities() {
        lenient().when(activities.assessTicket(any()))
                .thenReturn(new TicketAssessmentResponse("refined spec", "FEATURE", 1));
        lenient().when(activities.planImplementation(any()))
                .thenReturn(new PlanningResponse(artifactRef("plan"), new AttemptPolicy(2, 90, 1), 1));
        lenient().when(activities.prepareWorkspace(any()))
                .thenReturn(new WorkspaceResponse(workspaceRef(), "feature/agent-worker-engine-t04", 1));
        lenient().when(activities.implement(any()))
                .thenReturn(new ImplementationResponse(artifactRef("impl"), 1));
        lenient().when(activities.runQualityAssurance(any()))
                .thenReturn(new QaResult(true, 95, artifactRef("qa-report"), 1));
        lenient().when(activities.recordAttemptHistory(any()))
                .thenReturn(new AttemptHistoryResponse(true, 1));
        lenient().when(activities.manageSourceControl(any()))
                .thenReturn(new SourceControlResponse("https://example.com/pr/1", "DRAFT", 1));
        lenient().when(activities.sendNotification(any()))
                .thenReturn(new NotificationResponse(true, 1));
    }

    private static ArtifactRef artifactRef(String kind) {
        return new ArtifactRef("artifact://" + kind, kind, 1);
    }

    private static WorkspaceRef workspaceRef() {
        return new WorkspaceRef("workspace://run", 1);
    }

    private AgentWorkerWorkflow newStub(String workflowId) {
        return workflowClient.newWorkflowStub(AgentWorkerWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(workflowId).build());
    }

    private StartAgentWorkflowRequest newRequest(String workflowId) {
        return new StartAgentWorkflowRequest(workflowId, "ticket-1", "raw specification", 1);
    }

    private void awaitStage(AgentWorkerWorkflow stub, WorkflowStage expected) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (stub.currentStage() == expected) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(stub.currentStage()).isEqualTo(expected);
    }

    private void awaitStatus(AgentWorkerWorkflow stub, WorkflowRunStatus expected) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (stub.status() == expected) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(stub.status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("approve 신호 전에는 다음 단계로 진행하지 않는다")
    void gate_blocksProgressUntilApproved() throws Exception {
        AgentWorkerWorkflow stub = newStub("gate-blocks-run");
        WorkflowClient.execute(stub::run, newRequest("gate-blocks-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        Thread.sleep(200);

        assertThat(stub.currentStage()).isEqualTo(WorkflowStage.INTAKE);
        verify(activities, times(1)).assessTicket(any());
        verify(activities, never()).planImplementation(any());
    }

    @Test
    @DisplayName("모든 게이트를 순차 승인하면 COMPLETED로 끝난다")
    void approve_sequentialGatesCompleteRun() throws Exception {
        AgentWorkerWorkflow stub = newStub("approve-all-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("approve-all-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);
        stub.approve();

        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());
        verify(activities, times(1)).prepareWorkspace(any());
        verify(activities, times(1)).implement(any());
        verify(activities, times(1)).runQualityAssurance(any());
        verify(activities, times(2)).manageSourceControl(any());
    }

    @Test
    @DisplayName("QA 반려는 사유를 보존하고 IMPLEMENTATION 단계로 되돌아간다")
    void reject_atQaPreservesReasonAndReturnsToImplementation() throws Exception {
        AgentWorkerWorkflow stub = newStub("reject-qa-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("reject-qa-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);

        stub.reject("QA 점수 미달, 재구현 필요", WorkflowStage.IMPLEMENTATION);
        awaitStatus(stub, WorkflowRunStatus.PAUSED);

        stub.retryStage();
        awaitStage(stub, WorkflowStage.QA);
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);
        stub.approve();

        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());
        verify(activities, times(2)).implement(any());
        verify(activities, times(2)).runQualityAssurance(any());
    }

    @Test
    @DisplayName("QA 점수가 threshold와 정확히 같으면 통과 처리되고 재시도하지 않는다")
    void qaLoop_thresholdEquality_passesWithoutRetry() throws Exception {
        when(activities.runQualityAssurance(any()))
                .thenReturn(new QaResult(true, 90, artifactRef("qa-report"), 1));

        AgentWorkerWorkflow stub = newStub("threshold-eq-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("threshold-eq-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);
        stub.approve();

        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());
        verify(activities, times(1)).implement(any());
        verify(activities, times(1)).runQualityAssurance(any());
        verify(activities, times(1)).recordAttemptHistory(any());
    }

    @Test
    @DisplayName("마지막 Attempt(2회째)에서 통과하면 게이트 없이 자동으로 한 번 재시도한 뒤 완료된다")
    void qaLoop_passesOnLastAttempt_autoRetriesOnce() throws Exception {
        when(activities.runQualityAssurance(any()))
                .thenReturn(new QaResult(false, 50, artifactRef("qa-report-1"), 1))
                .thenReturn(new QaResult(true, 95, artifactRef("qa-report-2"), 1));

        AgentWorkerWorkflow stub = newStub("pass-last-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("pass-last-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);
        stub.approve();

        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());
        verify(activities, times(1)).prepareWorkspace(any());
        verify(activities, times(2)).implement(any());
        verify(activities, times(2)).runQualityAssurance(any());
        verify(activities, times(2)).recordAttemptHistory(any());
    }

    @Test
    @DisplayName("시도가 소진되면 더 이상 자동 재시도하지 않고 게이트로 진행한다")
    void qaLoop_exhaustsAttempts_stopsAutoRetryAndWaitsForGate() throws Exception {
        when(activities.runQualityAssurance(any()))
                .thenReturn(new QaResult(false, 50, artifactRef("qa-report"), 1));

        AgentWorkerWorkflow stub = newStub("exhaustion-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("exhaustion-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);
        // 시도 소진 후에는 사람이 게이트에서 결정 — approve는 미달 QA에도 강제 진행
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);
        stub.approve();

        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());
        verify(activities, times(2)).implement(any());
        verify(activities, times(2)).runQualityAssurance(any());
        verify(activities, times(2)).recordAttemptHistory(any());
    }

    @Test
    @DisplayName("MERGE는 QA 통과 및 REVIEW_MERGE approve 이전에 스케줄되지 않는다")
    void reviewMerge_mergeIsNotScheduledBeforeApproval() throws Exception {
        AgentWorkerWorkflow stub = newStub("merge-order-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("merge-order-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);

        // REVIEW_MERGE 게이트에 도달 — CREATE_DRAFT_PR은 이미 실행됐지만 아직 승인 전이라 MERGE는 없어야 한다
        verify(activities, times(1)).manageSourceControl(argThat(req -> "CREATE_DRAFT_PR".equals(req.action())));
        verify(activities, never()).manageSourceControl(argThat(req -> "MERGE".equals(req.action())));

        stub.approve();
        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());

        InOrder inOrder = inOrder(activities);
        inOrder.verify(activities).runQualityAssurance(any());
        inOrder.verify(activities).manageSourceControl(argThat(req -> "CREATE_DRAFT_PR".equals(req.action())));
        inOrder.verify(activities).manageSourceControl(argThat(req -> "MERGE".equals(req.action())));
    }

    @Test
    @DisplayName("cancel 신호는 언제든 CANCELLED로 종료한다")
    void cancel_terminatesRunAsCancelled() throws Exception {
        AgentWorkerWorkflow stub = newStub("cancel-run");
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest("cancel-run"));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.cancel();

        String result = future.get(10, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(WorkflowRunStatus.CANCELLED.name());
        verify(activities, never()).planImplementation(any());
    }

    @Test
    @DisplayName("완료된 실행의 History를 replay해도 예외가 발생하지 않는다")
    void replay_completedRunReplaysWithoutError() throws Exception {
        String workflowId = "replay-run";
        AgentWorkerWorkflow stub = newStub(workflowId);
        CompletableFuture<String> future = WorkflowClient.execute(stub::run, newRequest(workflowId));

        awaitStage(stub, WorkflowStage.INTAKE);
        stub.approve();
        awaitStage(stub, WorkflowStage.PLANNING);
        stub.approve();
        awaitStage(stub, WorkflowStage.QA);
        stub.approve();
        awaitStage(stub, WorkflowStage.REVIEW_MERGE);
        stub.approve();
        future.get(10, TimeUnit.SECONDS);

        GetWorkflowExecutionHistoryRequest request = GetWorkflowExecutionHistoryRequest.newBuilder()
                .setNamespace(testEnvironment.getNamespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId).build())
                .build();
        GetWorkflowExecutionHistoryResponse response = testEnvironment.getWorkflowServiceStubs()
                .blockingStub().getWorkflowExecutionHistory(request);
        WorkflowExecutionHistory history = new WorkflowExecutionHistory(response.getHistory(), workflowId);

        WorkflowReplayer.replayWorkflowExecution(history, AgentWorkerWorkflowImpl.class);
    }

    /** Plain delegate so the class registered with the Worker carries no copied Temporal annotations. */
    private record EngineActivitiesDelegate(EngineActivities delegate) implements EngineActivities {

        @Override
        public TicketAssessmentResponse assessTicket(TicketAssessmentRequest request) {
            return delegate.assessTicket(request);
        }

        @Override
        public PlanningResponse planImplementation(PlanningRequest request) {
            return delegate.planImplementation(request);
        }

        @Override
        public WorkspaceResponse prepareWorkspace(WorkspaceRequest request) {
            return delegate.prepareWorkspace(request);
        }

        @Override
        public ImplementationResponse implement(ImplementationRequest request) {
            return delegate.implement(request);
        }

        @Override
        public QaResult runQualityAssurance(QaRequest request) {
            return delegate.runQualityAssurance(request);
        }

        @Override
        public AttemptHistoryResponse recordAttemptHistory(AttemptHistoryRequest request) {
            return delegate.recordAttemptHistory(request);
        }

        @Override
        public SourceControlResponse manageSourceControl(SourceControlRequest request) {
            return delegate.manageSourceControl(request);
        }

        @Override
        public NotificationResponse sendNotification(NotificationRequest request) {
            return delegate.sendNotification(request);
        }
    }
}
