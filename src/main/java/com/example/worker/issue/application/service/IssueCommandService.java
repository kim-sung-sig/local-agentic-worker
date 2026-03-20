package com.example.worker.issue.application.service;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.issue.application.port.IssueEventPublisher;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class IssueCommandService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final IssueEventPublisher issueEventPublisher;

    public IssueCommandService(IssueRepository issueRepository,
                               ProjectRepository projectRepository,
                               IssueEventPublisher issueEventPublisher) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.issueEventPublisher = issueEventPublisher;
    }

    @Transactional
    public IssueId createIssue(UUID projectId, String title, String description, Priority priority) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        int nextNumber = issueRepository.findMaxIssueNumber(project.getId()) + 1;
        Issue issue = Issue.create(project.getId(), nextNumber, title, description, priority);
        issueRepository.save(issue);

        issueEventPublisher.publishIssueCreated(new IssueCreatedEvent(
                issue.getId().value(),
                issue.getIssueNumber().value(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getPriority().name(),
                project.getId().value(),
                project.getLocalPath().value(),
                project.getBaseBranch().value(),
                Instant.now()
        ));

        return issue.getId();
    }

    @Transactional
    public void updateStatus(UUID issueId, IssueStatus newStatus) {
        Issue issue = issueRepository.findById(IssueId.of(issueId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        issue.updateStatus(newStatus);
        issueRepository.save(issue);
    }
}
