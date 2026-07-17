package com.example.worker.controlplane;

import com.example.worker.issue.application.dto.CreateIssueCommand;
import com.example.worker.issue.application.dto.IssueSummary;
import com.example.worker.issue.application.service.IssueCommandService;
import com.example.worker.issue.application.service.IssueQueryService;
import com.example.worker.issue.domain.model.Priority;
import com.example.worker.project.application.dto.ProjectRegistrationCommand;
import com.example.worker.project.application.service.ProjectCommandService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ControlPlanePersistenceIntegrationTest.PersistenceConfig.class)
@DisplayName("Control Plane PostgreSQL 통합")
class ControlPlanePersistenceIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void requireDockerAndStartPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker daemon unavailable in this environment - skipping integration test");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ProjectCommandService projectCommandService;

    @Autowired
    private IssueCommandService issueCommandService;

    @Autowired
    private IssueQueryService issueQueryService;

    @Test
    @DisplayName("원격 URI 유니크 제약과 Project별 동시 Issue 번호 할당을 보장한다")
    void persistsRemoteProjectAndAllocatesIssueNumbersConcurrently() throws Exception {
        // Given
        ProjectRegistrationCommand project = new ProjectRegistrationCommand(
                "catalog", "https://github.com/acme/catalog.git", "main", "github-app/catalog");
        var projectId = projectCommandService.registerProject(project);

        // When / Then: V6 partial unique index is enforced by PostgreSQL.
        assertThatThrownBy(() -> projectCommandService.registerProject(project))
                .isInstanceOf(RuntimeException.class);

        // When: two transactions allocate Issue numbers for the same Project.
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> issueCommandService.createIssue(new CreateIssueCommand(
                    projectId.value(), "검색 API", "검색 API 구현", Priority.HIGH)));
            Future<?> second = executor.submit(() -> issueCommandService.createIssue(new CreateIssueCommand(
                    projectId.value(), "상품 API", "상품 API 구현", Priority.HIGH)));
            first.get();
            second.get();
        }

        // Then
        List<IssueSummary> issues = issueQueryService.listByProject(projectId.value());
        assertThat(issues).extracting(IssueSummary::issueNumber).containsExactlyInAnyOrder(1, 2);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = {
            "com.example.worker.project.infrastructure.datasource",
            "com.example.worker.issue.infrastructure.datasource"
    })
    @EnableJpaRepositories(basePackages = {
            "com.example.worker.project.infrastructure.datasource",
            "com.example.worker.issue.infrastructure.datasource"
    })
    @ComponentScan(basePackages = {
            "com.example.worker.project.application.service",
            "com.example.worker.issue.application.service",
            "com.example.worker.project.infrastructure.datasource",
            "com.example.worker.issue.infrastructure.datasource"
    })
    static class PersistenceConfig {
    }
}
