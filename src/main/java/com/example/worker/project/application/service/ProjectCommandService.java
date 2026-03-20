package com.example.worker.project.application.service;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCommandService {

    private final ProjectRepository projectRepository;

    public ProjectCommandService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectId registerProject(String name, String localPath, String baseBranch) {
        if (projectRepository.existsByLocalPath(localPath)) {
            throw new BusinessException(ErrorCode.PROJECT_PATH_DUPLICATED);
        }
        Project project = Project.create(name, localPath, baseBranch);
        projectRepository.save(project);
        return project.getId();
    }
}
