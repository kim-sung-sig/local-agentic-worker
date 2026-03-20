package com.example.worker.issue.application.port;

import com.example.worker.issue.event.model.IssueCreatedEvent;

public interface IssueEventPublisher {

    void publishIssueCreated(IssueCreatedEvent event);
}
