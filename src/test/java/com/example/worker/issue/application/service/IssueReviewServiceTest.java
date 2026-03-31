package com.example.worker.issue.application.service;

import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentJobStatus;
import com.example.worker.common.exception.BusinessException;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.IssueNumber;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.issue.event.model.IssueRejectedEvent;
import com.example.worker.project.domain.model.ProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("IssueReviewService")
@ExtendWith(MockitoExtension.class)
class IssueReviewServiceTest {

    @Mock IssueRepository issueRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AgentJobRepository agentJobRepository;

    IssueReviewService service;

    @BeforeEach
    void setUp() {
        service = new IssueReviewService(issueRepository, eventPublisher, agentJobRepository);
    }

    private Issue issueInReview() {
        IssueId id = IssueId.newId();
        Issue issue = Issue.reconstitute(
                id, new ProjectId(java.util.UUID.randomUUID()), new IssueNumber(1),
                "제목", "설명", Priority.MEDIUM, IssueStatus.IN_REVIEW, LocalDateTime.now());
        when(issueRepository.findById(id)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return issue;
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("승인 시 이슈 상태가 CLOSED로 변경된다")
        void approve_closesIssue() {
            Issue issue = issueInReview();

            service.approve(issue.getId());

            ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
            verify(issueRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(IssueStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("반려")
    class Reject {

        @Test
        @DisplayName("반려 시 이슈 상태가 REJECTED로 변경된다")
        void reject_setsStatusToRejected() {
            Issue issue = issueInReview();
            when(agentJobRepository.findByIssueId(issue.getId().value())).thenReturn(List.of());

            service.reject(issue.getId(), "테스트 코드 누락");

            ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
            verify(issueRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(IssueStatus.REJECTED);
        }

        @Test
        @DisplayName("반려 시 IssueRejectedEvent가 발행된다")
        void reject_publishesIssueRejectedEvent() {
            Issue issue = issueInReview();
            when(agentJobRepository.findByIssueId(issue.getId().value())).thenReturn(List.of());

            service.reject(issue.getId(), "피드백 내용");

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(IssueRejectedEvent.class);
        }

        @Test
        @DisplayName("반려 시 retryCount는 기존 AgentJob 수와 같다")
        void reject_retryCountEqualsJobCount() {
            Issue issue = issueInReview();
            AgentJob existingJob = AgentJob.reconstitute(
                    AgentJobId.newId(), issue.getId().value(), UUID.randomUUID(),
                    "feat/issue-1", AgentJobStatus.SUCCEEDED,
                    Instant.now(), Instant.now(), null, "https://github.com/pr/1");
            when(agentJobRepository.findByIssueId(issue.getId().value())).thenReturn(List.of(existingJob));

            service.reject(issue.getId(), "피드백");

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            IssueRejectedEvent event = (IssueRejectedEvent) captor.getValue();
            assertThat(event.retryCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("IN_REVIEW 상태 가드")
    class StatusGuard {

        @Test
        @DisplayName("IN_REVIEW가 아닌 이슈를 승인하면 예외가 발생한다")
        void approve_throwsIfNotInReview() {
            IssueId id = IssueId.newId();
            Issue openIssue = Issue.reconstitute(
                    id, new ProjectId(UUID.randomUUID()), new IssueNumber(1),
                    "제목", "설명", Priority.MEDIUM, IssueStatus.OPEN, LocalDateTime.now());
            when(issueRepository.findById(id)).thenReturn(Optional.of(openIssue));

            assertThatThrownBy(() -> service.approve(id))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
