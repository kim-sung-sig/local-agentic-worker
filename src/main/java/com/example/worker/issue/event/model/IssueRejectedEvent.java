package com.example.worker.issue.event.model;

import com.example.worker.issue.domain.model.IssueId;

public record IssueRejectedEvent(
        IssueId issueId,
        String feedback,
        int retryCount
) {}
