package com.example.worker.project.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Project")
class ProjectTest {

    @Nested
    @DisplayName("원격 Git 프로젝트 생성")
    class CreateRemote {

        @Test
        @DisplayName("로컬 경로 없이 원격 저장소 프로젝트를 생성한다")
        void createsRemoteProjectWithoutLocalPath() {
            // Given
            RepositoryUri repositoryUri = new RepositoryUri("https://github.com/acme/catalog.git");
            RemoteProjectRegistration registration = new RemoteProjectRegistration(
                    "catalog", repositoryUri, BranchName.of("main"), "github-app/catalog");

            // When
            Project project = Project.createRemote(registration);

            // Then
            assertEquals(repositoryUri, project.getRepositoryUri());
            assertEquals("main", project.getBaseBranch().value());
            assertEquals("github-app/catalog", project.getCredentialRef());
            assertNull(project.getLocalPath());
        }

        @Test
        @DisplayName("빈 인증 참조는 저장하지 않는다")
        void normalizesBlankCredentialReference() {
            // Given
            RemoteProjectRegistration registration = new RemoteProjectRegistration(
                    "catalog", new RepositoryUri("https://github.com/acme/catalog.git"),
                    BranchName.of("main"), "   ");

            // When
            Project project = Project.createRemote(registration);

            // Then
            assertNull(project.getCredentialRef());
        }
    }
}
