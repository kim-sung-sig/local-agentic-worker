package com.example.worker.engine.infrastructure.temporal;

import com.example.worker.engine.infrastructure.activity.EngineActivitiesImpl;
import com.example.worker.engine.workflow.AgentWorkerWorkflowImpl;
import com.example.worker.engine.workflow.EngineHealthWorkflowImpl;
import com.example.worker.runtime.application.WorkspaceRuntime;
import com.example.worker.runtime.infrastructure.git.GitWorktreeRuntime;
import com.example.worker.scm.application.SourceControlPlugin;
import com.example.worker.scm.infrastructure.github.GitHubCliSourceControlPlugin;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfiguration {

    @Value("${agent.engine.temporal.task-queue}")
    private String taskQueue;

    @Bean
    public WorkspaceRuntime workspaceRuntime(
            @Value("${agent.engine.runtime.source-repo}") String sourceRepo,
            @Value("${agent.engine.runtime.workspace-root}") String workspaceRoot) {
        return new GitWorktreeRuntime(sourceRepo, workspaceRoot);
    }

    @Bean
    public SourceControlPlugin sourceControlPlugin() {
        return new GitHubCliSourceControlPlugin();
    }

    @Bean
    public WorkerFactory engineWorkerFactory(WorkflowClient workflowClient, EngineActivitiesImpl engineActivities) {
        WorkerFactory workerFactory = WorkerFactory.newInstance(workflowClient);
        Worker worker = workerFactory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(EngineHealthWorkflowImpl.class, AgentWorkerWorkflowImpl.class);
        worker.registerActivitiesImplementations(engineActivities);
        return workerFactory;
    }

    @Bean
    public SmartLifecycle engineWorkerLifecycle(WorkerFactory engineWorkerFactory) {
        return new EngineWorkerLifecycle(engineWorkerFactory);
    }

    private static final class EngineWorkerLifecycle implements SmartLifecycle {

        private final WorkerFactory workerFactory;
        private volatile boolean running = false;

        private EngineWorkerLifecycle(WorkerFactory workerFactory) {
            this.workerFactory = workerFactory;
        }

        @Override
        public void start() {
            workerFactory.start();
            running = true;
        }

        @Override
        public void stop() {
            workerFactory.shutdown();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
