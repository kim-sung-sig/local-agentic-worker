package com.example.worker.issue.api.controller;

import com.example.worker.issue.api.request.ReviewIssueRequest;
import com.example.worker.issue.application.service.IssueReviewService;
import com.example.worker.issue.domain.model.IssueId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/issues")
public class IssueReviewController {

    private final IssueReviewService reviewService;

    public IssueReviewController(IssueReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<Void> review(@PathVariable UUID id,
                                       @Valid @RequestBody ReviewIssueRequest request) {
        IssueId issueId = IssueId.of(id);
        if (Boolean.TRUE.equals(request.approved())) {
            reviewService.approve(issueId);
        } else {
            reviewService.reject(issueId, request.feedback());
        }
        return ResponseEntity.noContent().build();
    }
}
