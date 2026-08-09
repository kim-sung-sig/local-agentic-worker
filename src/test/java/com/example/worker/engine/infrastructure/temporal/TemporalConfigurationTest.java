package com.example.worker.engine.infrastructure.temporal;

import com.example.worker.engine.infrastructure.activity.EngineActivitiesImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TemporalConfigurationTest {

    @Test
    void javaWorkerIsEnabledWhenPropertyIsMissing() throws NoSuchMethodException {
        ConditionalOnProperty condition = workerFactoryMethod().getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("agent.engine.temporal.worker-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void javaWorkerCanBeDisabledForPolyglotWorker() throws NoSuchMethodException {
        ConditionalOnProperty factoryCondition = workerFactoryMethod().getAnnotation(ConditionalOnProperty.class);
        ConditionalOnProperty lifecycleCondition = TemporalConfiguration.class
                .getDeclaredMethod("engineWorkerLifecycle", WorkerFactory.class)
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(factoryCondition).isNotNull();
        assertThat(lifecycleCondition).isNotNull();
        assertThat(lifecycleCondition.name()).containsExactly("agent.engine.temporal.worker-enabled");
        assertThat(lifecycleCondition.havingValue()).isEqualTo("true");
    }

    @Test
    void disabledPropertyDoesNotCreateJavaWorkerBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(TemporalConfiguration.class)
                .withBean(WorkflowClient.class, () -> mock(WorkflowClient.class))
                .withBean(EngineActivitiesImpl.class, () -> mock(EngineActivitiesImpl.class))
                .withPropertyValues(
                        "agent.engine.temporal.worker-enabled=false",
                        "agent.engine.temporal.task-queue=agent-worker-engine-typescript",
                        "agent.engine.runtime.source-repo=build/reference-source-repo",
                        "agent.engine.runtime.workspace-root=build/workspaces")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WorkerFactory.class);
                    assertThat(context).doesNotHaveBean("engineWorkerLifecycle");
                });
    }

    private static Method workerFactoryMethod() throws NoSuchMethodException {
        return TemporalConfiguration.class.getDeclaredMethod(
                "engineWorkerFactory", WorkflowClient.class, EngineActivitiesImpl.class);
    }
}
