package com.example.worker.project.application.service;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;
import com.example.worker.project.application.dto.ProjectDetail;
import com.example.worker.project.application.dto.ProjectSummary;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectQueryService {

    private final ProjectRepository projectRepository;

    public ProjectQueryService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetail getProject(UUID id) {
        return projectRepository.findById(ProjectId.of(id))
                .map(ProjectDetail::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
