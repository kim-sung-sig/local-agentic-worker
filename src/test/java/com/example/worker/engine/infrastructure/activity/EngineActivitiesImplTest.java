package com.example.worker.engine.infrastructure.activity;

import com.example.worker.engine.application.contract.v1.ActivityRequestMetadata;
import com.example.worker.engine.application.contract.v1.AttemptHistoryRequest;
import com.example.worker.engine.application.contract.v1.TicketAssessmentRequest;
import com.example.worker.engine.application.contract.v1.NotificationRequest;
import com.example.worker.engine.application.contract.v1.NotificationResponse;
import com.example.worker.contracts.agentworker.EngineNotificationRequested;
import com.example.worker.engine.application.port.NotificationPublisher;
import com.example.worker.engine.application.port.WorkflowRunRepository;
import com.example.worker.engine.domain.model.WorkflowRun;
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import com.example.worker.runtime.application.WorkspaceRuntime;
import com.example.worker.scm.application.SourceControlPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EngineActivitiesImpl")
class EngineActivitiesImplTest {

    private WorkspaceRuntime workspaceRuntime;
    private SourceControlPlugin sourceControlPlugin;
    private WorkflowRunRepository workflowRunRepository;
    private NotificationPublisher notificationPublisher;
    private EngineActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        workspaceRuntime = mock(WorkspaceRuntime.class);
        sourceControlPlugin = mock(SourceControlPlugin.class);
        workflowRunRepository = mock(WorkflowRunRepository.class);
        notificationPublisher = mock(NotificationPublisher.class);
        activities = new EngineActivitiesImpl(workspaceRuntime, sourceControlPlugin, workflowRunRepository, notificationPublisher);
    }

    private TicketAssessmentRequest assessTicketRequest(String workflowRunId) {
        return assessTicketRequest(workflowRunId, UUID.randomUUID().toString());
    }

    private TicketAssessmentRequest assessTicketRequest(String workflowRunId, String ticketId) {
        return new TicketAssessmentRequest(
                new ActivityRequestMetadata(workflowRunId, WorkflowStage.INTAKE, 1, 1),
                ticketId, "raw spec", 1);
    }

    @Test
    @DisplayName("assessTicket 호출 시점에 WorkflowRun을 INTAKE/RUNNING 상태로 즉시 영속화한다 (QA 단계까지 기다리지 않음)")
    void assessTicket_persistsWorkflowRunImmediately() {
        String workflowRunId = UUID.randomUUID().toString();
        when(workflowRunRepository.findByTemporalWorkflowId(workflowRunId))
                .thenReturn(Optional.empty());

        activities.assessTicket(assessTicketRequest(workflowRunId));

        ArgumentCaptor<WorkflowRun> captor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(workflowRunRepository).save(captor.capture());
        WorkflowRun saved = captor.getValue();
        assertThat(saved.getCurrentStage()).isEqualTo(WorkflowStage.INTAKE);
        assertThat(saved.getStatus()).isEqualTo(WorkflowRunStatus.RUNNING);
        assertThat(saved.getTemporalWorkflowId()).isEqualTo(workflowRunId);
    }

    @Test
    @DisplayName("워크플로 생성 시 요청 ticketId를 저장한다")
    void assessTicket_storesRequestedTicketId() {
        String workflowRunId = UUID.randomUUID().toString();
        UUID ticketId = UUID.randomUUID();
        when(workflowRunRepository.findByTemporalWorkflowId(workflowRunId)).thenReturn(Optional.empty());

        activities.assessTicket(assessTicketRequest(workflowRunId, ticketId.toString()));

        ArgumentCaptor<WorkflowRun> captor = ArgumentCaptor.forClass(WorkflowRun.class);
        verify(workflowRunRepository).save(captor.capture());
        assertThat(captor.getValue().getTicketId()).isEqualTo(ticketId);
    }

    @Test
    @DisplayName("이미 WorkflowRun이 존재하면(Activity 재시도) 다시 생성하지 않는다")
    void assessTicket_whenWorkflowRunAlreadyExists_doesNotRecreate() {
        String workflowRunId = UUID.randomUUID().toString();
        WorkflowRun existing = WorkflowRun.create(UUID.fromString(workflowRunId), workflowRunId);
        when(workflowRunRepository.findByTemporalWorkflowId(workflowRunId)).thenReturn(Optional.of(existing));

        activities.assessTicket(assessTicketRequest(workflowRunId));

        verify(workflowRunRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("recordAttemptHistory를 두 번 호출하면 새 행을 만들지 않고 같은 WorkflowRun에 시도를 누적한다")
    void recordAttemptHistory_calledTwice_accumulatesOnSameWorkflowRun() {
        String workflowRunId = UUID.randomUUID().toString();

        // Simulates two sequential Activity calls (attempt 1, then attempt 2) against a real
        // repository: each call's findByTemporalWorkflowId sees whatever the previous save() wrote.
        java.util.concurrent.atomic.AtomicReference<WorkflowRun> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(workflowRunRepository.findByTemporalWorkflowId(workflowRunId))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(workflowRunRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            WorkflowRun run = inv.getArgument(0);
            stored.set(run);
            return run;
        });

        activities.assessTicket(assessTicketRequest(workflowRunId));

        activities.recordAttemptHistory(new AttemptHistoryRequest(
                new ActivityRequestMetadata(workflowRunId, WorkflowStage.QA, 1, 1),
                null, null, 50, "FAILED", 1));
        activities.recordAttemptHistory(new AttemptHistoryRequest(
                new ActivityRequestMetadata(workflowRunId, WorkflowStage.QA, 2, 1),
                null, null, 95, "PASSED", 1));

        WorkflowRun run = stored.get();
        assertThat(run.getAttempts()).hasSize(2);
        assertThat(run.getTemporalWorkflowId()).isEqualTo(workflowRunId);
    }

    @Test
    @DisplayName("초기와 후속 알림은 같은 영속 WorkflowRun aggregate ID를 Kafka 메시지로 발행한다 (Issue/Notification을 직접 호출하지 않음)")
    void sendNotification_publishesSamePersistedAggregateIdBeforeAndAfterAssessment() {
        String temporalWorkflowId = UUID.randomUUID().toString();
        UUID ticketId = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<WorkflowRun> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(workflowRunRepository.findByTemporalWorkflowId(temporalWorkflowId))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(workflowRunRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            WorkflowRun run = invocation.getArgument(0); stored.set(run); return run;
        });
        NotificationRequest created = new NotificationRequest(
                new ActivityRequestMetadata(temporalWorkflowId, WorkflowStage.INTAKE, 1, 1), ticketId.toString(),
                "WORKFLOW_CREATED", "INFO", "생성", "시작", 1);
        NotificationRequest started = new NotificationRequest(
                new ActivityRequestMetadata(temporalWorkflowId, WorkflowStage.INTAKE, 1, 1), ticketId.toString(),
                "ACTIVITY_STARTED", "INFO", "시작", "분석", 1);

        activities.sendNotification(created);
        activities.assessTicket(assessTicketRequest(temporalWorkflowId, ticketId.toString()));
        activities.sendNotification(started);

        ArgumentCaptor<EngineNotificationRequested> events = ArgumentCaptor.forClass(EngineNotificationRequested.class);
        verify(notificationPublisher, org.mockito.Mockito.times(2)).publish(events.capture());
        assertThat(events.getAllValues().stream().map(EngineNotificationRequested::workflowRunId).distinct())
                .containsExactly(stored.get().getId().value().toString());
        assertThat(events.getAllValues().stream().map(EngineNotificationRequested::ticketId).distinct())
                .containsExactly(ticketId.toString());
        assertThat(events.getAllValues().get(0).type()).isEqualTo("WORKFLOW_CREATED");
        assertThat(events.getAllValues().get(1).type()).isEqualTo("ACTIVITY_STARTED");
    }
}
