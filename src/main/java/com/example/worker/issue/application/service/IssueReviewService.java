package com.example.worker.issue.application.service;

import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.event.model.IssueRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class IssueReviewService {

    private final IssueRepository issueRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentJobRepository agentJobRepository;

    public IssueReviewService(IssueRepository issueRepository,
                               ApplicationEventPublisher eventPublisher,
                               AgentJobRepository agentJobRepository) {
        this.issueRepository = issueRepository;
        this.eventPublisher = eventPublisher;
        this.agentJobRepository = agentJobRepository;
    }

    public void approve(IssueId issueId) {
        Issue issue = findInReview(issueId);
        issue.updateStatus(IssueStatus.CLOSED);
        issueRepository.save(issue);
    }

    public void reject(IssueId issueId, String feedback) {
        Issue issue = findInReview(issueId);
        int retryCount = agentJobRepository.findByIssueId(issueId.value()).size();
        issue.updateStatus(IssueStatus.REJECTED);
        issueRepository.save(issue);
        eventPublisher.publishEvent(new IssueRejectedEvent(issueId, feedback, retryCount));
    }

    private Issue findInReview(IssueId issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        if (issue.getStatus() != IssueStatus.IN_REVIEW) {
            throw new BusinessException(ErrorCode.ISSUE_STATUS_TRANSITION_INVALID);
        }
        return issue;
    }
}
