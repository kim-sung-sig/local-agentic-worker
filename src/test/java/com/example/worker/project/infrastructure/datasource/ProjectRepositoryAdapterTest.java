package com.example.worker.project.infrastructure.datasource;

import com.example.worker.project.domain.model.BranchName;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.RemoteProjectRegistration;
import com.example.worker.project.domain.model.RepositoryUri;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectRepositoryAdapter")
@ExtendWith(MockitoExtension.class)
class ProjectRepositoryAdapterTest {

    @Mock
    ProjectJpaRepository jpaRepository;

    ProjectRepositoryAdapter adapter;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() {
        adapter = new ProjectRepositoryAdapter(jpaRepository);
    }

    @Nested
    @DisplayName("원격 Project 저장")
    class SaveRemoteProject {

        @Test
        @DisplayName("원격 저장소 정보가 보존된 Project를 반환한다")
        void preservesRemoteRepositoryFields() {
            // Given
            Project remoteProject = Project.createRemote(new RemoteProjectRegistration(
                    "catalog", new RepositoryUri("https://github.com/acme/catalog.git"),
                    BranchName.of("main"), "github-app/catalog"));
            when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Project savedProject = adapter.save(remoteProject);

            // Then
            assertThat(savedProject.getRepositoryUri()).isEqualTo(remoteProject.getRepositoryUri());
            assertThat(savedProject.getCredentialRef()).isEqualTo("github-app/catalog");
            assertThat(savedProject.getLocalPath()).isNull();
        }
    }

    @Nested
    @DisplayName("기존 로컬 Project 저장")
    class SaveLocalProject {

        @Test
        @DisplayName("기존 로컬 경로 Project를 계속 반환한다")
        void preservesLegacyLocalPath() {
            // Given
            Project localProject = Project.create("catalog", temporaryDirectory.toString(), "main");
            when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Project savedProject = adapter.save(localProject);

            // Then
            assertThat(savedProject.getLocalPath()).isEqualTo(localProject.getLocalPath());
            assertThat(savedProject.getRepositoryUri()).isNull();
        }
    }

    @Nested
    @DisplayName("원격 저장소 중복 확인")
    class ExistsByRepositoryUri {

        @Test
        @DisplayName("저장소 URI로 중복 여부를 조회한다")
        void checksRepositoryUri() {
            // Given
            RepositoryUri repositoryUri = new RepositoryUri("https://github.com/acme/catalog.git");
            when(jpaRepository.existsByRepositoryUri(repositoryUri.value())).thenReturn(true);

            // When
            boolean exists = adapter.existsByRepositoryUri(repositoryUri);

            // Then
            assertThat(exists).isTrue();
            verify(jpaRepository).existsByRepositoryUri(repositoryUri.value());
        }
    }
}
