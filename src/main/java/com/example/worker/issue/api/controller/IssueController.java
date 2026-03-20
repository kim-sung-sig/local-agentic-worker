package com.example.worker.issue.api.controller;

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
import java.util.List;
import java.util.UUID;

@RestController
public class IssueController {

    private final IssueCommandService commandService;
    private final IssueQueryService queryService;

    public IssueController(IssueCommandService commandService, IssueQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
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
}
