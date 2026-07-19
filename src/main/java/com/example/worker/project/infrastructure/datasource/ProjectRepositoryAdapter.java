package com.example.worker.project.infrastructure.datasource;

import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import com.example.worker.project.domain.model.RepositoryUri;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public ProjectRepositoryAdapter(ProjectJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Project save(Project project) {
        return jpaRepository.save(ProjectJpaEntity.from(project)).toDomain();
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return jpaRepository.findById(id.value()).map(ProjectJpaEntity::toDomain);
    }

    @Override
    public Optional<Project> findByIdForUpdate(ProjectId id) {
        return Optional.ofNullable(entityManager.find(ProjectJpaEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE))
                .map(ProjectJpaEntity::toDomain);
    }

    @Override
    public boolean existsByLocalPath(String localPath) {
        return jpaRepository.existsByLocalPath(localPath);
    }

    @Override
    public boolean existsByRepositoryUri(RepositoryUri repositoryUri) {
        return jpaRepository.existsByRepositoryUri(repositoryUri.value());
    }

    @Override
    public List<Project> findAll() {
        return jpaRepository.findAll().stream()
                .map(ProjectJpaEntity::toDomain)
                .toList();
    }
}
