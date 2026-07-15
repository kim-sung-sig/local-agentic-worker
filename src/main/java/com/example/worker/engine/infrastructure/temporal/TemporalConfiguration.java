package com.example.worker.engine.infrastructure.temporal;

import com.example.worker.engine.workflow.AgentWorkerWorkflowImpl;
import com.example.worker.engine.workflow.EngineHealthWorkflowImpl;
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
    public WorkerFactory engineWorkerFactory(WorkflowClient workflowClient) {
        WorkerFactory workerFactory = WorkerFactory.newInstance(workflowClient);
        Worker worker = workerFactory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(EngineHealthWorkflowImpl.class, AgentWorkerWorkflowImpl.class);
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
