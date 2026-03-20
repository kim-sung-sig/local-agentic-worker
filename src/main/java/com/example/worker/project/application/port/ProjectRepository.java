package com.example.worker.project.application.port;

import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(ProjectId id);

    boolean existsByLocalPath(String localPath);

    List<Project> findAll();
}
