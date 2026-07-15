package com.example.worker.engine.api.controller;

import com.example.worker.engine.application.service.AgentWorkerStarter;
import com.example.worker.engine.application.port.AttemptRecordRepository;
import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.AttemptStatus;
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import com.example.worker.engine.workflow.AgentWorkerWorkflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkflowRunController.class)
@DisplayName("WorkflowRunController")
class WorkflowRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentWorkerStarter starter;

    @MockBean
    private WorkflowClient workflowClient;

    @MockBean
    private AttemptRecordRepository attemptRecordRepository;

    private AgentWorkerWorkflow workflowStub;

    @BeforeEach
    void setUp() {
        workflowStub = mock(AgentWorkerWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(AgentWorkerWorkflow.class), anyString()))
                .thenReturn(workflowStub);
    }

    @Test
    @DisplayName("정상 시작 요청은 202와 workflowRunId를 반환한다")
    void start_validRequest_returnsAccepted() throws Exception {
        mockMvc.perform(post("/api/engine/workflow-runs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new com.example.worker.engine.api.request.StartWorkflowRequest(
                                        "ticket-1", "raw spec"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflowRunId").exists())
                .andExpect(jsonPath("$.currentStage").value("INTAKE"));
    }

    @Test
    @DisplayName("빈 ticketId로 시작 요청하면 400을 반환한다")
    void start_blankTicketId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/engine/workflow-runs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new com.example.worker.engine.api.request.StartWorkflowRequest(
                                        "", "raw spec"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정상 조회는 현재 단계/상태를 반환한다")
    void get_existingRun_returnsCurrentStageAndStatus() throws Exception {
        when(workflowStub.currentStage()).thenReturn(WorkflowStage.PLANNING);
        when(workflowStub.status()).thenReturn(WorkflowRunStatus.RUNNING);

        mockMvc.perform(get("/api/engine/workflow-runs/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("PLANNING"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("존재하지 않는 Workflow Run 조회는 404를 반환한다")
    void get_missingRun_returnsNotFound() throws Exception {
        when(workflowStub.currentStage()).thenThrow(new WorkflowNotFoundException(
                WorkflowExecution.newBuilder().setWorkflowId("missing-run").build(),
                "AgentWorkerWorkflow", new RuntimeException("not found")));

        mockMvc.perform(get("/api/engine/workflow-runs/missing-run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("Attempt 이력 조회는 산출물/QA 참조/점수/상태/시각을 반환한다")
    void attempts_returnsRecordedAttempts() throws Exception {
        AttemptRecord record = new AttemptRecord(1,
                "artifact://run-1/impl-1", "artifact://run-1/qa-1", 95,
                AttemptStatus.PASSED, Instant.now(), Instant.now());
        when(attemptRecordRepository.findByWorkflowRunId(any())).thenReturn(List.of(record));

        mockMvc.perform(get("/api/engine/workflow-runs/{id}/attempts",
                        "123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptNumber").value(1))
                .andExpect(jsonPath("$[0].qaScore").value(95))
                .andExpect(jsonPath("$[0].implementationArtifactRef").value("artifact://run-1/impl-1"));
    }

    @Test
    @DisplayName("targetStage 없는 REJECT 결정은 400을 반환하고 Signal을 보내지 않는다")
    void decide_rejectWithoutTargetStage_isRejected() throws Exception {
        mockMvc.perform(post("/api/engine/workflow-runs/run-1/decisions")
                        .contentType("application/json")
                        .content("""
                                {"decision":"REJECT","reason":"needs work"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE_DECISION"));

        verify(workflowStub, never()).reject(anyString(), any());
    }

    @Test
    @DisplayName("reason 없는 REQUEST_REVISION 결정은 400을 반환하고 Signal을 보내지 않는다")
    void decide_requestRevisionWithoutReason_isRejected() throws Exception {
        mockMvc.perform(post("/api/engine/workflow-runs/run-1/decisions")
                        .contentType("application/json")
                        .content("""
                                {"decision":"REQUEST_REVISION"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE_DECISION"));

        verify(workflowStub, never()).requestRevision(anyString());
    }

    @Test
    @DisplayName("정상 APPROVE 결정은 202를 반환하고 approve Signal을 호출한다")
    void decide_approve_sendsSignal() throws Exception {
        mockMvc.perform(post("/api/engine/workflow-runs/run-1/decisions")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isAccepted());

        verify(workflowStub).approve();
    }
}
