package com.example.worker.agent.api.controller;

import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobStatus;
import com.example.worker.agent.domain.model.AgentPhase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan / Design 페이즈 산출물 문서를 조회하는 API.
 *
 * GET /api/issues/{issueId}/agent/documents/plan    → plan.md 내용 반환
 * GET /api/issues/{issueId}/agent/documents/design  → design.md 내용 반환
 * GET /api/issues/{issueId}/agent/phases            → 페이즈별 상태 목록
 */
@RestController
@RequestMapping("/api/issues/{issueId}/agent")
public class AgentDocumentController {

    private final AgentJobRepository agentJobRepository;

    public AgentDocumentController(AgentJobRepository agentJobRepository) {
        this.agentJobRepository = agentJobRepository;
    }

    /**
     * Plan 문서 내용 반환 (Markdown 원문).
     */
    @GetMapping("/documents/plan")
    public ResponseEntity<Map<String, String>> getPlanDocument(@PathVariable UUID issueId) {
        return findDocument(issueId, AgentPhase.PLAN);
    }

    /**
     * Design 문서 내용 반환 (Markdown 원문).
     */
    @GetMapping("/documents/design")
    public ResponseEntity<Map<String, String>> getDesignDocument(@PathVariable UUID issueId) {
        return findDocument(issueId, AgentPhase.DESIGN);
    }

    /**
     * 이슈의 모든 페이즈 실행 기록 반환.
     */
    @GetMapping("/phases")
    public ResponseEntity<List<PhaseStatusResponse>> getPhases(@PathVariable UUID issueId) {
        List<PhaseStatusResponse> phases = agentJobRepository.findByIssueId(issueId).stream()
                .map(PhaseStatusResponse::from)
                .toList();
        return ResponseEntity.ok(phases);
    }

    // ─────────────────────────────────────────────

    private ResponseEntity<Map<String, String>> findDocument(UUID issueId, AgentPhase phase) {
        Optional<AgentJob> jobOpt = agentJobRepository.findByIssueId(issueId).stream()
                .filter(j -> j.getPhase() == phase && j.getStatus() == AgentJobStatus.SUCCEEDED)
                .max(java.util.Comparator.comparing(AgentJob::getStartedAt));

        if (jobOpt.isEmpty() || jobOpt.get().getDocumentPath() == null) {
            return ResponseEntity.notFound().build();
        }

        String docPath = jobOpt.get().getDocumentPath();
        try {
            String content = Files.readString(Path.of(docPath));
            return ResponseEntity.ok(Map.of(
                    "phase", phase.name(),
                    "path", docPath,
                    "content", content
            ));
        } catch (IOException e) {
            return ResponseEntity.ok(Map.of(
                    "phase", phase.name(),
                    "path", docPath,
                    "content", "(문서가 아직 생성되지 않았거나 경로를 확인하세요: " + docPath + ")"
            ));
        }
    }

    // ─────────────────────────────────────────────
    // Response DTO
    // ─────────────────────────────────────────────

    record PhaseStatusResponse(
            String jobId,
            String phase,
            String status,
            String documentPath,
            String prUrl,
            String errorMessage,
            String startedAt,
            String finishedAt
    ) {
        static PhaseStatusResponse from(AgentJob job) {
            return new PhaseStatusResponse(
                    job.getId().value().toString(),
                    job.getPhase().name(),
                    job.getStatus().name(),
                    job.getDocumentPath(),
                    job.getPrUrl(),
                    job.getErrorMessage(),
                    job.getStartedAt() != null ? job.getStartedAt().toString() : null,
                    job.getFinishedAt() != null ? job.getFinishedAt().toString() : null
            );
        }
    }
}
