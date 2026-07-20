package com.example.worker.engine.integration;

import com.example.worker.contracts.agentworker.EngineNotificationRequested;
import com.example.worker.engine.application.port.AttemptRecordRepository;
import com.example.worker.engine.application.port.NotificationPublisher;
import com.example.worker.engine.application.port.WorkflowRunRepository;
import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.AttemptStatus;
import com.example.worker.engine.domain.model.WorkflowRunId;
import com.example.worker.engine.domain.model.WorkflowRunStatus;
import com.example.worker.engine.domain.model.WorkflowStage;
import com.example.worker.engine.infrastructure.activity.EngineActivitiesImpl;
import com.example.worker.engine.workflow.AgentWorkerWorkflow;
import com.example.worker.engine.workflow.AgentWorkerWorkflowImpl;
import com.example.worker.engine.workflow.StartAgentWorkflowRequest;
import com.example.worker.runtime.infrastructure.git.GitWorktreeRuntime;
import com.example.worker.scm.application.SourceControlPlugin;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full six-stage flow against real Temporal test infrastructure and a real
 * PostgreSQL (Testcontainers) instance — verifying T02 (persistence), T05 (workspace runtime),
 * T06 (QA loop) and the workflow/gate semantics from T04 all work together end to end.
 *
 * <p>Skips gracefully (not a failure) when no Docker daemon is available, consistent with the
 * Testcontainers-dependent integration scenarios already deferred in the T02/T05 analyses.
 */
@SpringBootTest(classes = AgentWorkerEngineIntegrationTest.MinimalPersistenceConfig.class)
@DisplayName("Agent Worker Engine end-to-end integration")
class AgentWorkerEngineIntegrationTest {

    private static final String TASK_QUEUE = "agent-worker-engine-integration-test";

    // Deliberately NOT using @Testcontainers/@Container: that extension starts the container
    // in its own beforeAll callback before our Docker-availability check below can run, turning
    // a graceful skip into a hard failure. Starting it ourselves, after assumeTrue, avoids that.
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void requireDockerAndStartPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker daemon unavailable in this environment - skipping integration test");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private AttemptRecordRepository attemptRecordRepository;

    @Test
    @DisplayName("Intake부터 병합 완료까지 승인과 함께 실행하면 WorkspaceRef 하나를 재사용하고 Attempt가 실제 PostgreSQL에 저장된다")
    void fullSixStageFlow_reusesWorkspaceAndPersistsAttempt() throws Exception {
        Path sourceRepo = Files.createTempDirectory("t08-source-repo");
        Path runtimeRoot = Files.createTempDirectory("t08-workspaces");
        initGitRepo(sourceRepo);

        GitWorktreeRuntime workspaceRuntime = new GitWorktreeRuntime(sourceRepo.toString(), runtimeRoot.toString());
        FakeSourceControlPlugin sourceControlPlugin = new FakeSourceControlPlugin();
        RecordingNotificationPublisher notificationPublisher = new RecordingNotificationPublisher();
        EngineActivitiesImpl activities = new EngineActivitiesImpl(
                workspaceRuntime, sourceControlPlugin, workflowRunRepository, notificationPublisher);

        TestWorkflowEnvironment testEnvironment = TestWorkflowEnvironment.newInstance();
        try {
            Worker worker = testEnvironment.newWorker(TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(AgentWorkerWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            testEnvironment.start();

            WorkflowClient workflowClient = testEnvironment.getWorkflowClient();
            String workflowRunId = UUID.randomUUID().toString();
            String ticketId = UUID.randomUUID().toString();
            AgentWorkerWorkflow stub = workflowClient.newWorkflowStub(AgentWorkerWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(workflowRunId).build());

            CompletableFuture<String> future = WorkflowClient.execute(stub::run,
                    new StartAgentWorkflowRequest(workflowRunId, ticketId, "raw specification", 1));

            awaitStage(stub, WorkflowStage.INTAKE);
            stub.approve();
            awaitStage(stub, WorkflowStage.PLANNING);
            stub.approve();
            awaitStage(stub, WorkflowStage.QA);
            stub.approve();
            awaitStage(stub, WorkflowStage.REVIEW_MERGE);
            stub.approve();

            String result = future.get(30, TimeUnit.SECONDS);
            assertThat(result).isEqualTo(WorkflowRunStatus.COMPLETED.name());

            // T05: 하나의 WorkspaceRef만 생성됐는지 실제 워크트리 디렉터리로 확인
            assertThat(runtimeRoot.resolve(workflowRunId)).isDirectory();
            assertThat(Files.list(runtimeRoot).count()).isEqualTo(1);

            // T02: Attempt가 실제 PostgreSQL(Testcontainers)에 저장됐는지 확인
            WorkflowRunId persistedRunId = workflowRunRepository.findByTemporalWorkflowId(workflowRunId)
                    .orElseThrow()
                    .getId();
            List<AttemptRecord> attempts = attemptRecordRepository.findByWorkflowRunId(
                    persistedRunId);
            assertThat(attempts).hasSize(1);
            AttemptRecord attempt = attempts.get(0);
            assertThat(attempt.status()).isEqualTo(AttemptStatus.PASSED);
            assertThat(attempt.qaScore()).isEqualTo(95);
            assertThat(attempt.implementationArtifactRef()).startsWith("artifact://" + workflowRunId);
            assertThat(attempt.qaReportRef()).startsWith("artifact://" + workflowRunId);
            assertThat(attempt.createdAt()).isNotNull();
            assertThat(attempt.finishedAt()).isNotNull();
        } finally {
            testEnvironment.close();
        }
    }

    private void awaitStage(AgentWorkerWorkflow stub, WorkflowStage expected) throws InterruptedException {
        for (int i = 0; i < 400; i++) {
            if (stub.currentStage() == expected) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(stub.currentStage()).isEqualTo(expected);
    }

    private static void initGitRepo(Path repo) throws IOException, InterruptedException {
        run(repo, "git", "init", "-b", "main");
        run(repo, "git", "config", "user.email", "integration-test@example.com");
        run(repo, "git", "config", "user.name", "Integration Test");
        Files.writeString(repo.resolve("README.md"), "integration test repo");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "init");
    }

    private static void run(Path workDir, String... cmd) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd)
                .directory(new File(workDir.toString()))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", cmd) + "\n" + output);
        }
    }

    /** Records published notifications instead of requiring a real Kafka broker in this test. */
    private static final class RecordingNotificationPublisher implements NotificationPublisher {

        private final List<EngineNotificationRequested> published = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publish(EngineNotificationRequested event) {
            published.add(event);
        }
    }

    /** In-memory test double avoiding real GitHub CLI/auth — mirrors T07's GitHubCliSourceControlPlugin contract. */
    private static final class FakeSourceControlPlugin implements SourceControlPlugin {

        private final Map<String, PullRequestResult> pullRequestsByBranch = new ConcurrentHashMap<>();

        @Override
        public PullRequestResult createDraftPullRequest(CreateDraftPullRequestCommand command) {
            if (!command.qaPassed()) {
                throw new IllegalStateException("Cannot create a draft PR without a passed QA attempt");
            }
            return pullRequestsByBranch.computeIfAbsent(command.branchName(),
                    branch -> new PullRequestResult("https://example.com/pr/" + branch, "DRAFT"));
        }

        @Override
        public PullRequestResult getPullRequest(String workspacePath, String branchName) {
            return pullRequestsByBranch.get(branchName);
        }

        @Override
        public PullRequestResult mergePullRequest(MergePullRequestCommand command) {
            PullRequestResult existing = pullRequestsByBranch.get(command.branchName());
            if (existing == null) {
                throw new IllegalStateException("Cannot merge: no draft PR exists for branch " + command.branchName());
            }
            PullRequestResult merged = new PullRequestResult(existing.url(), "MERGED");
            pullRequestsByBranch.put(command.branchName(), merged);
            return merged;
        }
    }

    /**
     * Minimal, Temporal/Kafka-free Spring context: just enough JPA + Flyway wiring (scoped to the
     * engine persistence package) to get real {@link WorkflowRunRepository}/{@link AttemptRecordRepository}
     * beans backed by the Testcontainers PostgreSQL instance.
     */
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.example.worker.engine.infrastructure.datasource")
    @EnableJpaRepositories(basePackages = "com.example.worker.engine.infrastructure.datasource")
    @ComponentScan(basePackages = "com.example.worker.engine.infrastructure.datasource")
    static class MinimalPersistenceConfig {
    }
}
