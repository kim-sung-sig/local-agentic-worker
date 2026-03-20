package com.example.worker.issue.application.listener;

import com.example.worker.agent.event.model.IssueStatusChangedEvent;
import com.example.worker.issue.application.service.IssueCommandService;
import com.example.worker.issue.domain.model.IssueStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class IssueStatusChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(IssueStatusChangedEventListener.class);

    private final IssueCommandService issueCommandService;

    public IssueStatusChangedEventListener(IssueCommandService issueCommandService) {
        this.issueCommandService = issueCommandService;
    }

    @EventListener
    public void on(IssueStatusChangedEvent event) {
        IssueStatus newStatus = IssueStatus.valueOf(event.newStatus());
        log.info("[Issue] 상태 변경: {} → {}", event.issueId(), newStatus);
        issueCommandService.updateStatus(event.issueId(), newStatus);
    }
}
