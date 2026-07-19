package com.example.worker.project.application.port;

import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import com.example.worker.project.domain.model.RepositoryUri;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(ProjectId id);

    Optional<Project> findByIdForUpdate(ProjectId id);

    boolean existsByLocalPath(String localPath);

    boolean existsByRepositoryUri(RepositoryUri repositoryUri);

    List<Project> findAll();
}
