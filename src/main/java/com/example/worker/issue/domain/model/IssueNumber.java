package com.example.worker.issue.domain.model;

public record IssueNumber(int value) {

    public IssueNumber {
        if (value < 1) throw new IllegalArgumentException("이슈 번호는 1 이상이어야 합니다.");
    }
}
