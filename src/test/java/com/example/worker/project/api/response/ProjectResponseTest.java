package com.example.worker.project.api.response;

import com.example.worker.project.application.dto.ProjectSummary;
import com.example.worker.project.domain.model.BranchName;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.RemoteProjectRegistration;
import com.example.worker.project.domain.model.RepositoryUri;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectResponse")
class ProjectResponseTest {

    @Test
    @DisplayName("원격 저장소 URI를 노출하고 인증 참조를 노출하지 않는다")
    void exposesRepositoryUriWithoutCredentialReference() {
        // Given
        Project project = Project.createRemote(new RemoteProjectRegistration(
                "catalog", new RepositoryUri("https://github.com/acme/catalog.git"),
                BranchName.of("main"), "github-app/catalog"));

        // When
        ProjectResponse response = ProjectResponse.from(ProjectSummary.from(project));

        // Then
        assertThat(response.repositoryUri()).isEqualTo("https://github.com/acme/catalog.git");
        assertThat(response.toString()).doesNotContain("github-app/catalog");
    }
}
