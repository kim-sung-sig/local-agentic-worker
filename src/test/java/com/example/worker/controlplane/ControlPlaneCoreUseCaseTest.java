package com.example.worker.controlplane;

import com.example.worker.issue.application.dto.CreateIssueCommand;
import com.example.worker.issue.application.dto.IssueSummary;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.application.service.IssueCommandService;
import com.example.worker.issue.application.service.IssueQueryService;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.project.api.response.ProjectResponse;
import com.example.worker.project.application.dto.ProjectDetail;
import com.example.worker.project.application.dto.ProjectRegistrationCommand;
import com.example.worker.project.application.dto.ProjectSummary;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.application.service.ProjectCommandService;
import com.example.worker.project.application.service.ProjectQueryService;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import com.example.worker.project.domain.model.RepositoryUri;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@DisplayName("Control Plane 핵심 use case")
class ControlPlaneCoreUseCaseTest {

    @Test
    @DisplayName("원격 Project와 Issue를 Agent 또는 Sync 없이 관리한다")
    void managesRemoteProjectAndIssueWithoutAgentOrSync() {
        // Given
        InMemoryProjectRepository projectRepository = new InMemoryProjectRepository();
        InMemoryIssueRepository issueRepository = new InMemoryIssueRepository();
        ProjectCommandService projectCommandService = new ProjectCommandService(projectRepository);
        ProjectQueryService projectQueryService = new ProjectQueryService(projectRepository);
        IssueCommandService issueCommandService = new IssueCommandService(issueRepository, projectRepository);
        IssueQueryService issueQueryService = new IssueQueryService(issueRepository);

        // When
        var projectId = projectCommandService.registerProject(new ProjectRegistrationCommand(
                "catalog", "https://github.com/acme/catalog.git", "main", "github-app/catalog"));
        ProjectDetail projectDetail = projectQueryService.getProject(projectId.value());
        var issueId = issueCommandService.createIssue(new CreateIssueCommand(
                projectId.value(), "상품 검색 추가", "검색 API를 추가한다", Priority.HIGH));
        issueCommandService.updateStatus(issueId.value(), IssueStatus.PLAN_IN_PROGRESS);

        // Then
        assertThat(ProjectResponse.from(projectDetail).repositoryUri())
                .isEqualTo("https://github.com/acme/catalog.git");
        assertThat(ProjectResponse.from(projectDetail).toString()).doesNotContain("github-app/catalog");
        assertThat(projectQueryService.listProjects())
                .extracting(ProjectSummary::id)
                .containsExactly(projectId.value());
        assertThat(issueQueryService.listByProject(projectId.value()))
                .extracting(IssueSummary::issueNumber)
                .containsExactly(1);
        assertThat(issueQueryService.getIssue(issueId.value()).status())
                .isEqualTo(IssueStatus.PLAN_IN_PROGRESS.name());
    }

    private static final class InMemoryProjectRepository implements ProjectRepository {

        private final Map<UUID, Project> projects = new HashMap<>();

        @Override
        public Project save(Project project) {
            projects.put(project.getId().value(), project);
            return project;
        }

        @Override
        public Optional<Project> findById(ProjectId id) {
            return Optional.ofNullable(projects.get(id.value()));
        }

        @Override
        public Optional<Project> findByIdForUpdate(ProjectId id) {
            return findById(id);
        }

        @Override
        public boolean existsByLocalPath(String localPath) {
            return projects.values().stream()
                    .anyMatch(project -> project.getLocalPath() != null
                            && project.getLocalPath().value().equals(localPath));
        }

        @Override
        public boolean existsByRepositoryUri(RepositoryUri repositoryUri) {
            return projects.values().stream()
                    .anyMatch(project -> repositoryUri.equals(project.getRepositoryUri()));
        }

        @Override
        public List<Project> findAll() {
            return List.copyOf(projects.values());
        }
    }

    private static final class InMemoryIssueRepository implements IssueRepository {

        private final Map<UUID, Issue> issues = new HashMap<>();

        @Override
        public Issue save(Issue issue) {
            issues.put(issue.getId().value(), issue);
            return issue;
        }

        @Override
        public Optional<Issue> findById(IssueId id) {
            return Optional.ofNullable(issues.get(id.value()));
        }

        @Override
        public List<Issue> findByProjectId(ProjectId projectId) {
            return issues.values().stream()
                    .filter(issue -> issue.getProjectId().equals(projectId))
                    .toList();
        }

        @Override
        public int findMaxIssueNumber(ProjectId projectId) {
            return findByProjectId(projectId).stream()
                    .mapToInt(issue -> issue.getIssueNumber().value())
                    .max()
                    .orElse(0);
        }
    }
}
