package com.example.worker.issue.domain.model;

public enum IssueStatus {
    OPEN,
    // Plan 단계
    PLAN_IN_PROGRESS,
    PLAN_DONE,
    // Design 단계
    DESIGN_IN_PROGRESS,
    DESIGN_DONE,
    // Develop 단계
    DEV_IN_PROGRESS,
    // 검토
    IN_REVIEW,
    REJECTED,
    FAILED,
    CLOSED
}
