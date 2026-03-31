package com.example.worker.issue.domain.model;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.project.domain.model.ProjectId;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Issue {

    private final IssueId id;
    private final ProjectId projectId;
    private final IssueNumber issueNumber;
    private final String title;
    private final String description;
    private final Priority priority;
    private IssueStatus status;
    private final LocalDateTime createdAt;

    private Issue(IssueId id, ProjectId projectId, IssueNumber issueNumber,
                  String title, String description, Priority priority,
                  IssueStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.issueNumber = issueNumber;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Issue create(ProjectId projectId, int nextNumber,
                               String title, String description, Priority priority) {
        return new Issue(
                IssueId.newId(),
                projectId,
                new IssueNumber(nextNumber),
                title,
                description,
                priority,
                IssueStatus.OPEN,
                LocalDateTime.now()
        );
    }

    public static Issue reconstitute(IssueId id, ProjectId projectId, IssueNumber issueNumber,
                                     String title, String description, Priority priority,
                                     IssueStatus status, LocalDateTime createdAt) {
        return new Issue(id, projectId, issueNumber, title, description, priority, status, createdAt);
    }

    public void updateStatus(IssueStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new BusinessException(ErrorCode.ISSUE_STATUS_TRANSITION_INVALID);
        }
        this.status = newStatus;
    }

    private boolean isValidTransition(IssueStatus from, IssueStatus to) {
        return switch (from) {
            case OPEN               -> to == IssueStatus.PLAN_IN_PROGRESS;
            case PLAN_IN_PROGRESS   -> to == IssueStatus.PLAN_DONE   || to == IssueStatus.FAILED;
            case PLAN_DONE          -> to == IssueStatus.DESIGN_IN_PROGRESS || to == IssueStatus.DEV_IN_PROGRESS;
            case DESIGN_IN_PROGRESS -> to == IssueStatus.DESIGN_DONE || to == IssueStatus.FAILED;
            case DESIGN_DONE        -> to == IssueStatus.DEV_IN_PROGRESS;
            case DEV_IN_PROGRESS    -> to == IssueStatus.IN_REVIEW   || to == IssueStatus.FAILED;
            case IN_REVIEW          -> to == IssueStatus.CLOSED || to == IssueStatus.REJECTED || to == IssueStatus.DEV_IN_PROGRESS;
            case REJECTED           -> to == IssueStatus.DEV_IN_PROGRESS;
            case FAILED             -> to == IssueStatus.PLAN_IN_PROGRESS || to == IssueStatus.CLOSED;
            case CLOSED             -> false;
        };
    }

}
