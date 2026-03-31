package com.example.worker.agent.infrastructure.kafka;

import com.example.worker.issue.event.model.IssueCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka에서 이슈 생성 이벤트를 수신한다.
 * 에이전트 자동 실행은 하지 않는다 — 사용자가 웹 UI에서 페이즈를 수동 트리거한다.
 * (POST /api/issues/{id}/agent/plan|design|develop)
 */
@Component
public class IssueCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IssueCreatedEventConsumer.class);

    @KafkaListener(topics = "issue-created", groupId = "agent-worker")
    public void consume(IssueCreatedEvent event) {
        log.info("[AgentWorker] 이슈 수신 (수동 트리거 대기): #{} - {} (project: {})",
                event.issueNumber(), event.title(), event.projectId());
        // 자동 실행 없음 — UI에서 페이즈 트리거
    }
}
