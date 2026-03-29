package com.example.worker.issue.api.controller;

import com.example.worker.agent.api.response.AgentJobResponse;
import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.issue.api.request.CreateIssueRequest;
import com.example.worker.issue.api.request.UpdateIssueStatusRequest;
import com.example.worker.issue.api.response.IssueResponse;
import com.example.worker.issue.application.service.IssueCommandService;
import com.example.worker.issue.application.service.IssueQueryService;
import com.example.worker.issue.domain.model.IssueId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
public class IssueController {

    private final IssueCommandService commandService;
    private final IssueQueryService queryService;
    private final AgentJobRepository agentJobRepository;

    public IssueController(IssueCommandService commandService, IssueQueryService queryService,
                           AgentJobRepository agentJobRepository) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.agentJobRepository = agentJobRepository;
    }

    @PostMapping("/api/projects/{projectId}/issues")
    public ResponseEntity<Void> createIssue(@PathVariable UUID projectId,
                                            @Valid @RequestBody CreateIssueRequest request) {
        IssueId id = commandService.createIssue(
                projectId, request.title(), request.description(), request.priority());
        return ResponseEntity.created(URI.create("/api/issues/" + id.value())).build();
    }

    @GetMapping("/api/projects/{projectId}/issues")
    public ResponseEntity<List<IssueResponse>> listIssues(@PathVariable UUID projectId) {
        List<IssueResponse> body = queryService.listByProject(projectId).stream()
                .map(IssueResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/issues/{id}")
    public ResponseEntity<IssueResponse> getIssue(@PathVariable UUID id) {
        return ResponseEntity.ok(IssueResponse.from(queryService.getIssue(id)));
    }

    @PatchMapping("/api/issues/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateIssueStatusRequest request) {
        commandService.updateStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/issues/{id}/agent-job")
    public ResponseEntity<AgentJobResponse> getAgentJob(@PathVariable UUID id) {
        return agentJobRepository.findByIssueId(id).stream()
                .max(Comparator.comparing(AgentJob::getStartedAt))
                .map(AgentJobResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
