package com.example.worker.project.infrastructure.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, UUID> {

    boolean existsByLocalPath(String localPath);

    boolean existsByRepositoryUri(String repositoryUri);
}
