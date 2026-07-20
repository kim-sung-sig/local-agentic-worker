package com.example.worker.engine.application.port;

import com.example.worker.contracts.agentworker.EngineNotificationRequested;

/**
 * Publishes a workflow notification for Control Plane to consume. Agent Engine never resolves
 * Issue/Project state itself — see {@link EngineNotificationRequested}.
 */
public interface NotificationPublisher {

    void publish(EngineNotificationRequested event);
}
