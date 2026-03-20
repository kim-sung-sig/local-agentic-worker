package com.example.worker.issue.infrastructure.kafka;

import com.example.worker.issue.application.port.IssueEventPublisher;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaIssueEventPublisher implements IssueEventPublisher {

    private static final String TOPIC_ISSUE_CREATED = "issue-created";

    private final KafkaTemplate<String, IssueCreatedEvent> kafkaTemplate;

    public KafkaIssueEventPublisher(KafkaTemplate<String, IssueCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishIssueCreated(IssueCreatedEvent event) {
        kafkaTemplate.send(TOPIC_ISSUE_CREATED, event);
    }
}
