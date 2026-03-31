package com.example.worker.agent.api.controller;

import com.example.worker.agent.domain.model.AgentPhase;
import com.example.worker.agent.event.model.AgentPhaseRequestedEvent;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 이슈의 에이전트 페이즈를 수동으로 트리거하는 API.
 *
 * POST /api/issues/{id}/agent/plan          → PLAN 페이즈만
 * POST /api/issues/{id}/agent/design        → DESIGN 페이즈만
 * POST /api/issues/{id}/agent/develop       → DEVELOP 페이즈만
 * POST /api/issues/{id}/agent/plan-design   → PLAN → DESIGN 순차 실행
 */
@RestController
@RequestMapping("/api/issues/{issueId}/agent")
public class AgentPhaseTriggerController {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentPhaseTriggerController(IssueRepository issueRepository,
                                        ProjectRepository projectRepository,
                                        ApplicationEventPublisher eventPublisher) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/plan")
    public ResponseEntity<Void> startPlan(@PathVariable UUID issueId) {
        publishPhase(issueId, AgentPhase.PLAN);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/design")
    public ResponseEntity<Void> startDesign(@PathVariable UUID issueId) {
        publishPhase(issueId, AgentPhase.DESIGN);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/develop")
    public ResponseEntity<Void> startDevelop(@PathVariable UUID issueId) {
        publishPhase(issueId, AgentPhase.DEVELOP);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/plan-design")
    public ResponseEntity<Void> startPlanAndDesign(@PathVariable UUID issueId) {
        publishPhase(issueId, AgentPhase.PLAN);
        publishPhase(issueId, AgentPhase.DESIGN);
        return ResponseEntity.accepted().build();
    }

    // ─────────────────────────────────────────────

    private void publishPhase(UUID issueId, AgentPhase phase) {
        Issue issue = issueRepository.findById(IssueId.of(issueId))
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));

        Project project = projectRepository.findById(ProjectId.of(issue.getProjectId().value()))
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        eventPublisher.publishEvent(new AgentPhaseRequestedEvent(
                issueId,
                phase,
                project.getId().value(),
                project.getLocalPath().value(),
                project.getBaseBranch().value(),
                issue.getIssueNumber().value(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getPriority().name()
        ));
    }
}
