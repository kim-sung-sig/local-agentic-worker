package com.example.worker.project.infrastructure.datasource;

import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;

    public ProjectRepositoryAdapter(ProjectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
    public boolean existsByLocalPath(String localPath) {
        return jpaRepository.existsByLocalPath(localPath);
    }

    @Override
    public List<Project> findAll() {
        return jpaRepository.findAll().stream()
                .map(ProjectJpaEntity::toDomain)
                .toList();
    }
}
