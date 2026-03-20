package com.example.worker.issue.application.service;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.issue.application.dto.IssueSummary;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IssueQueryService {

    private final IssueRepository issueRepository;

    public IssueQueryService(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    @Transactional(readOnly = true)
    public List<IssueSummary> listByProject(UUID projectId) {
        return issueRepository.findByProjectId(ProjectId.of(projectId)).stream()
                .map(IssueSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IssueSummary getIssue(UUID issueId) {
        return issueRepository.findById(IssueId.of(issueId))
                .map(IssueSummary::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
    }
}
