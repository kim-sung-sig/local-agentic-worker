package com.example.worker.project.application.service;

import com.example.worker.project.application.dto.ProjectRegistrationCommand;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.project.domain.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectCommandService")
@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceTest {

    @Mock
    ProjectRepository projectRepository;

    ProjectCommandService service;

    @BeforeEach
    void setUp() {
        service = new ProjectCommandService(projectRepository);
    }

    @Nested
    @DisplayName("원격 Git Project 등록")
    class RegisterProject {

        @Test
        @DisplayName("원격 저장소 정보로 Project를 저장한다")
        void savesRemoteProject() {
            // Given
            ProjectRegistrationCommand command = new ProjectRegistrationCommand(
                    "catalog", "https://github.com/acme/catalog.git", "main", "github-app/catalog");
            when(projectRepository.existsByRepositoryUri(any())).thenReturn(false);
            when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.registerProject(command);

            // Then
            ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
            verify(projectRepository).save(projectCaptor.capture());
            assertThat(projectCaptor.getValue().getRepositoryUri().value())
                    .isEqualTo("https://github.com/acme/catalog.git");
            assertThat(projectCaptor.getValue().getLocalPath()).isNull();
        }

        @Test
        @DisplayName("동일한 원격 저장소 URI는 거부한다")
        void rejectsDuplicateRepositoryUri() {
            // Given
            ProjectRegistrationCommand command = new ProjectRegistrationCommand(
                    "catalog", "https://github.com/acme/catalog.git", "main", null);
            when(projectRepository.existsByRepositoryUri(any())).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> service.registerProject(command))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.PROJECT_REPOSITORY_URI_DUPLICATED));
        }
    }
}
