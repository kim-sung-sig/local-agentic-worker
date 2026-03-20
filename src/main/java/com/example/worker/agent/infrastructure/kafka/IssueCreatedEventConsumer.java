package com.example.worker.agent.infrastructure.kafka;

import com.example.worker.agent.application.service.AgentWorkerService;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IssueCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IssueCreatedEventConsumer.class);

    private final AgentWorkerService agentWorkerService;

    public IssueCreatedEventConsumer(AgentWorkerService agentWorkerService) {
        this.agentWorkerService = agentWorkerService;
    }

    @KafkaListener(topics = "issue-created", groupId = "agent-worker")
    public void consume(IssueCreatedEvent event) {
        log.info("[AgentWorker] 이슈 수신: #{} - {} (project: {}, repo: {})",
                event.issueNumber(),
                event.title(),
                event.projectId(),
                event.projectLocalPath());
        agentWorkerService.handle(event);
    }
}
