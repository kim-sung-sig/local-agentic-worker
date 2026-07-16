package com.example.worker.engine.infrastructure.activity;

import com.example.worker.engine.application.contract.v1.ActivityRequestMetadata;
import com.example.worker.engine.application.contract.v1.AttemptHistoryRequest;
import com.example.worker.engine.application.contract.v1.TicketAssessmentRequest;
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
    private EngineActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        workspaceRuntime = mock(WorkspaceRuntime.class);
        sourceControlPlugin = mock(SourceControlPlugin.class);
        workflowRunRepository = mock(WorkflowRunRepository.class);
        activities = new EngineActivitiesImpl(workspaceRuntime, sourceControlPlugin, workflowRunRepository);
    }

    private TicketAssessmentRequest assessTicketRequest(String workflowRunId) {
        return new TicketAssessmentRequest(
                new ActivityRequestMetadata(workflowRunId, WorkflowStage.INTAKE, 1, 1),
                "ticket-1", "raw spec", 1);
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
}
