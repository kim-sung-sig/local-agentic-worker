package com.example.worker.engine.infrastructure.notification;

import com.example.worker.contracts.agentworker.EngineNotificationRequested;
import com.example.worker.engine.application.port.NotificationPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaNotificationPublisher implements NotificationPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public KafkaNotificationPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(EngineNotificationRequested event) {
        kafkaTemplate.send(EngineNotificationRequested.TOPIC, event.workflowRunId(), event);
    }
}
