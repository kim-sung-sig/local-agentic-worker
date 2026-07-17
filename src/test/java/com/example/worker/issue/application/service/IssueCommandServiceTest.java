package com.example.worker.issue.application.service;

import com.example.worker.issue.application.dto.CreateIssueCommand;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.BranchName;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import com.example.worker.project.domain.model.RemoteProjectRegistration;
import com.example.worker.project.domain.model.RepositoryUri;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IssueCommandService")
@ExtendWith(MockitoExtension.class)
class IssueCommandServiceTest {

    @Mock
    IssueRepository issueRepository;

    @Mock
    ProjectRepository projectRepository;

    IssueCommandService service;

    @BeforeEach
    void setUp() {
        service = new IssueCommandService(issueRepository, projectRepository);
    }

    @Nested
    @DisplayName("원격 Project Issue 생성")
    class CreateIssue {

        @Test
        @DisplayName("Agent 또는 Kafka 없이 OPEN Issue를 저장한다")
        void savesOpenIssueWithoutAgentOrKafka() {
            // Given
            Project project = Project.createRemote(new RemoteProjectRegistration(
                    "catalog", new RepositoryUri("https://github.com/acme/catalog.git"),
                    BranchName.of("main"), null));
            CreateIssueCommand command = new CreateIssueCommand(
                    project.getId().value(), "상품 검색 추가", "검색 API를 추가한다", Priority.HIGH);
            when(projectRepository.findByIdForUpdate(any())).thenReturn(Optional.of(project));
            when(issueRepository.findMaxIssueNumber(project.getId())).thenReturn(3);
            when(issueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.createIssue(command);

            // Then
            ArgumentCaptor<Issue> issueCaptor = ArgumentCaptor.forClass(Issue.class);
            verify(issueRepository).save(issueCaptor.capture());
            assertThat(issueCaptor.getValue().getIssueNumber().value()).isEqualTo(4);
            assertThat(issueCaptor.getValue().getStatus()).isEqualTo(IssueStatus.OPEN);
        }

        @Test
        @DisplayName("존재하지 않는 Project에는 Issue를 생성할 수 없다")
        void rejectsMissingProject() {
            // Given
            CreateIssueCommand command = new CreateIssueCommand(
                    UUID.randomUUID(), "상품 검색 추가", "검색 API를 추가한다", Priority.HIGH);
            when(projectRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.createIssue(command))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Issue 상태 변경")
    class UpdateStatus {

        @Test
        @DisplayName("허용된 다음 상태로 변경한 Issue를 저장한다")
        void savesIssueWithNextStatus() {
            // Given
            Issue issue = Issue.create(new ProjectId(UUID.randomUUID()), 1,
                    "상품 검색 추가", "검색 API를 추가한다", Priority.HIGH);
            when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));
            when(issueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.updateStatus(issue.getId().value(), IssueStatus.PLAN_IN_PROGRESS);

            // Then
            assertThat(issue.getStatus()).isEqualTo(IssueStatus.PLAN_IN_PROGRESS);
            verify(issueRepository).save(issue);
        }
    }
}
