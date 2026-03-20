package com.example.worker.issue.domain.model;

import java.util.UUID;

public record IssueId(UUID value) {

    public static IssueId newId() {
        return new IssueId(UUID.randomUUID());
    }

    public static IssueId of(UUID value) {
        return new IssueId(value);
    }
}
