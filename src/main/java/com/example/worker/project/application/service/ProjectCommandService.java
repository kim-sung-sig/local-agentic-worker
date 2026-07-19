package com.example.worker.project.application.service;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.application.dto.ProjectRegistrationCommand;
import com.example.worker.project.domain.model.BranchName;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import com.example.worker.project.domain.model.RemoteProjectRegistration;
import com.example.worker.project.domain.model.RepositoryUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProjectCommandService.class);

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

    @Transactional
    public ProjectId registerProject(ProjectRegistrationCommand command) {
        RepositoryUri repositoryUri = new RepositoryUri(command.repositoryUri());
        log.info(">>> [원격 Project 등록] repositoryUri={}", repositoryUri.value());

        if (projectRepository.existsByRepositoryUri(repositoryUri)) {
            throw new BusinessException(ErrorCode.PROJECT_REPOSITORY_URI_DUPLICATED);
        }

        Project project = Project.createRemote(new RemoteProjectRegistration(
                command.name(), repositoryUri, BranchName.of(command.baseBranch()), command.credentialRef()));
        projectRepository.save(project);
        log.info("<<< [원격 Project 등록 완료] projectId={}", project.getId().value());
        return project.getId();
    }
}
