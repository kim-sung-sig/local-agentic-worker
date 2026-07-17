package com.example.worker.issue.application.service;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.issue.application.dto.CreateIssueCommand;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Service
public class IssueCommandService {

    private static final Logger log = LoggerFactory.getLogger(IssueCommandService.class);

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;

    public IssueCommandService(IssueRepository issueRepository,
                               ProjectRepository projectRepository) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public IssueId createIssue(CreateIssueCommand command) {
        log.info(">>> [Issue 생성] projectId={}", command.projectId());

        Project project = projectRepository.findByIdForUpdate(ProjectId.of(command.projectId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        int nextNumber = issueRepository.findMaxIssueNumber(project.getId()) + 1;
        Issue issue = Issue.create(project.getId(), nextNumber,
                command.title(), command.description(), command.priority());
        issueRepository.save(issue);

        log.info("<<< [Issue 생성 완료] issueId={}", issue.getId().value());
        return issue.getId();
    }

    @Transactional
    public void updateStatus(UUID issueId, IssueStatus newStatus) {
        log.info(">>> [Issue 상태 변경] issueId={}", issueId);
        Issue issue = issueRepository.findById(IssueId.of(issueId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        issue.updateStatus(newStatus);
        issueRepository.save(issue);
        log.info("<<< [Issue 상태 변경 완료] issueId={}", issueId);
    }
}
