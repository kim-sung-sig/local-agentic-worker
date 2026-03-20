package com.example.worker.project.api.controller;

import com.example.worker.project.api.request.CreateProjectRequest;
import com.example.worker.project.api.response.ProjectResponse;
import com.example.worker.project.application.service.ProjectCommandService;
import com.example.worker.project.application.service.ProjectQueryService;
import com.example.worker.project.domain.model.ProjectId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectCommandService commandService;
    private final ProjectQueryService queryService;

    public ProjectController(ProjectCommandService commandService, ProjectQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<Void> registerProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectId id = commandService.registerProject(request.name(), request.localPath(), request.baseBranch());
        return ResponseEntity.created(URI.create("/api/projects/" + id.value())).build();
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects() {
        List<ProjectResponse> body = queryService.listProjects().stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
        return ResponseEntity.ok(ProjectResponse.from(queryService.getProject(id)));
    }
}
