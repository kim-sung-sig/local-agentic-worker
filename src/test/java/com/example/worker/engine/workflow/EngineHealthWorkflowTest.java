package com.example.worker.engine.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EngineHealthWorkflow")
class EngineHealthWorkflowTest {

    private static final String TASK_QUEUE = "agent-worker-engine-test";

    private TestWorkflowEnvironment testEnvironment;
    private WorkflowClient workflowClient;

    @BeforeEach
    void setUp() {
        testEnvironment = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnvironment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(EngineHealthWorkflowImpl.class);
        testEnvironment.start();
        workflowClient = testEnvironment.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnvironment.close();
    }

    @Test
    @DisplayName("run() 호출 시 Temporal을 통해 \"ok\"를 반환한다")
    void run_returnsOk() {
        EngineHealthWorkflow workflow = workflowClient.newWorkflowStub(
                EngineHealthWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

        String result = workflow.run();

        assertThat(result).isEqualTo("ok");
    }
}
