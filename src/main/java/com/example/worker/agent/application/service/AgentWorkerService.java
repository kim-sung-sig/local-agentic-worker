package com.example.worker.agent.application.service;

import com.example.worker.agent.application.exception.AgentExecutionException;
import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentPhase;
import com.example.worker.agent.event.model.AgentPhaseRequestedEvent;
import com.example.worker.agent.event.model.IssueStatusChangedEvent;
import com.example.worker.agent.infrastructure.config.AgentProperties;
import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.issue.domain.model.IssueStatus;
import com.example.worker.issue.event.model.IssueRejectedEvent;
import com.example.worker.project.application.port.ProjectRepository;
import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Slf4j
@Service
public class AgentWorkerService {

    private final AgentJobRepository agentJobRepository;
    private final GitBranchService gitBranchService;
    private final ClaudeAgentExecutor claudeAgentExecutor;
    private final PullRequestService pullRequestService;
    private final ApplicationEventPublisher eventPublisher;
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final AgentProperties agentProperties;

    public AgentWorkerService(AgentJobRepository agentJobRepository,
                               GitBranchService gitBranchService,
                               ClaudeAgentExecutor claudeAgentExecutor,
                               PullRequestService pullRequestService,
                               ApplicationEventPublisher eventPublisher,
                               IssueRepository issueRepository,
                               ProjectRepository projectRepository,
                               AgentProperties agentProperties) {
        this.agentJobRepository = agentJobRepository;
        this.gitBranchService = gitBranchService;
        this.claudeAgentExecutor = claudeAgentExecutor;
        this.pullRequestService = pullRequestService;
        this.eventPublisher = eventPublisher;
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.agentProperties = agentProperties;
    }

    // ─────────────────────────────────────────────
    // Phase 라우터 — AgentPhaseRequestedEvent 수신
    // ─────────────────────────────────────────────

    @EventListener
    public void handlePhaseRequested(AgentPhaseRequestedEvent event) {
        switch (event.phase()) {
            case PLAN    -> executePlanPhase(event);
            case DESIGN  -> executeDesignPhase(event);
            case DEVELOP -> executeDevelopPhase(event);
        }
    }

    // ─────────────────────────────────────────────
    // PLAN 페이즈
    // ─────────────────────────────────────────────

    private void executePlanPhase(AgentPhaseRequestedEvent event) {
        String slug = PromptBuilder.toFeatureSlug(event.issueNumber(), event.issueTitle());
        String branchName = "feat/" + slug;

        AgentJob job = agentJobRepository.save(
                AgentJob.create(event.issueId(), event.projectId(), branchName, AgentPhase.PLAN));
        publishStatus(event.issueId(), IssueStatus.PLAN_IN_PROGRESS);

        try {
            job.startPlanning();
            agentJobRepository.save(job);
            gitBranchService.createBranch(event.projectLocalPath(), event.baseBranch(), branchName);

            PhaseResult result = claudeAgentExecutor.executePhase(
                    event.projectLocalPath(),
                    PromptBuilder.buildPlanPhase(toCreatedEvent(event), slug),
                    job.getId());

            String docPath = "docs/01-plan/features/" + slug + ".plan.md";
            job.completeWithDocument(docPath, result.sessionId());  // 세션 ID 저장
            agentJobRepository.save(job);
            publishStatus(event.issueId(), IssueStatus.PLAN_DONE);
            log.info("[Agent] PLAN 완료 이슈 #{} → {} (session: {})",
                    event.issueNumber(), docPath, result.sessionId());

        } catch (AgentExecutionException e) {
            log.error("[Agent] PLAN 실패 이슈 #{}: {}", event.issueNumber(), e.getMessage());
            job.fail("[PLAN] " + e.getMessage());
            agentJobRepository.save(job);
            publishStatus(event.issueId(), IssueStatus.FAILED);
        }
    }

    // ─────────────────────────────────────────────
    // DESIGN 페이즈
    // ─────────────────────────────────────────────

    private void executeDesignPhase(AgentPhaseRequestedEvent event) {
        String slug = PromptBuilder.toFeatureSlug(event.issueNumber(), event.issueTitle());
        String branchName = "feat/" + slug;

        AgentJob job = agentJobRepository.save(
                AgentJob.create(event.issueId(), event.projectId(), branchName, AgentPhase.DESIGN));
        publishStatus(event.issueId(), IssueStatus.DESIGN_IN_PROGRESS);

        try {
            job.startPlanning();
            agentJobRepository.save(job);

            // PLAN 페이즈의 세션 ID를 이어받아 Claude가 plan 문서 컨텍스트를 유지하도록 한다
            String planSessionId = agentJobRepository
                    .findLatestSucceededByIssueIdAndPhase(event.issueId(), AgentPhase.PLAN)
                    .map(AgentJob::getClaudeSessionId)
                    .orElse(null);

            if (planSessionId != null) {
                log.info("[Agent] DESIGN: PLAN 세션 재개 (session: {})", planSessionId);
            } else {
                log.warn("[Agent] DESIGN: PLAN 세션 없음 — 새 세션으로 시작");
            }

            PhaseResult result = claudeAgentExecutor.resumePhase(
                    event.projectLocalPath(),
                    PromptBuilder.buildDesignPhase(slug),
                    planSessionId,
                    job.getId());

            String docPath = "docs/02-design/features/" + slug + ".design.md";
            job.completeWithDocument(docPath, result.sessionId());  // 세션 ID 저장
            agentJobRepository.save(job);
            publishStatus(event.issueId(), IssueStatus.DESIGN_DONE);
            log.info("[Agent] DESIGN 완료 이슈 #{} → {} (session: {})",
                    event.issueNumber(), docPath, result.sessionId());

        } catch (AgentExecutionException e) {
            log.error("[Agent] DESIGN 실패 이슈 #{}: {}", event.issueNumber(), e.getMessage());
            job.fail("[DESIGN] " + e.getMessage());
            agentJobRepository.save(job);
            publishStatus(event.issueId(), IssueStatus.FAILED);
        }
    }

    // ─────────────────────────────────────────────
    // DEVELOP 페이즈 — do + analysis 핑퐁 루프
    // ─────────────────────────────────────────────

    private void executeDevelopPhase(AgentPhaseRequestedEvent event) {
        String slug = PromptBuilder.toFeatureSlug(event.issueNumber(), event.issueTitle());
        String branchName = "feat/" + slug;

        AgentJob job = agentJobRepository.save(
                AgentJob.create(event.issueId(), event.projectId(), branchName, AgentPhase.DEVELOP));
        publishStatus(event.issueId(), IssueStatus.DEV_IN_PROGRESS);

        try {
            // DESIGN 세션 → PLAN 세션 순으로 이어받을 세션 ID 탐색
            String priorSessionId = agentJobRepository
                    .findLatestSucceededByIssueIdAndPhase(event.issueId(), AgentPhase.DESIGN)
                    .map(AgentJob::getClaudeSessionId)
                    .or(() -> agentJobRepository
                            .findLatestSucceededByIssueIdAndPhase(event.issueId(), AgentPhase.PLAN)
                            .map(AgentJob::getClaudeSessionId))
                    .orElse(null);

            if (priorSessionId != null) {
                log.info("[Agent] DEVELOP: 이전 세션 재개 (session: {})", priorSessionId);
            } else {
                log.warn("[Agent] DEVELOP: 이전 세션 없음 — 새 세션으로 시작");
            }

            job.startCoding();
            agentJobRepository.save(job);

            String prUrl = runDoAndVerifyLoop(
                    event.projectLocalPath(), slug, branchName,
                    priorSessionId, job, null, 0);

            job.complete(prUrl);
            agentJobRepository.save(job);
            publishStatus(event.issueId(), IssueStatus.IN_REVIEW);
            log.info("[Agent] DEVELOP 완료 이슈 #{}: {}", event.issueNumber(), prUrl);

        } catch (AgentExecutionException e) {
            log.error("[Agent] DEVELOP 실패 이슈 #{}: {}", event.issueNumber(), e.getMessage());
            job.fail("[DEVELOP] " + e.getMessage());
            agentJobRepository.save(job);
            publishStatus(event.issueId(), IssueStatus.FAILED);
        }
    }

    // ─────────────────────────────────────────────
    // 반려 후 재시도
    // ─────────────────────────────────────────────

    @EventListener
    public void handleRejected(IssueRejectedEvent event) {
        Issue issue = issueRepository.findById(event.issueId())
                .orElseThrow(() -> new IllegalStateException("Issue not found: " + event.issueId()));

        AgentJob previousJob = agentJobRepository.findByIssueId(event.issueId().value()).stream()
                .max(Comparator.comparing(AgentJob::getStartedAt))
                .orElseThrow(() -> new IllegalStateException("AgentJob not found: " + event.issueId()));

        Project project = projectRepository.findById(ProjectId.of(previousJob.getProjectId()))
                .orElseThrow(() -> new IllegalStateException("Project not found: " + previousJob.getProjectId()));

        String slug = PromptBuilder.toFeatureSlug(
                issue.getIssueNumber().value(), issue.getTitle());
        String branchName = previousJob.getBranchName();

        AgentJob job = agentJobRepository.save(
                AgentJob.create(event.issueId().value(), previousJob.getProjectId(), branchName, AgentPhase.DEVELOP));
        issue.updateStatus(IssueStatus.DEV_IN_PROGRESS);
        issueRepository.save(issue);
        publishStatus(event.issueId().value(), IssueStatus.DEV_IN_PROGRESS);

        try {
            job.startPlanning();
            agentJobRepository.save(job);

            PhaseResult planResult = claudeAgentExecutor.executePhase(
                    project.getLocalPath().value(),
                    PromptBuilder.buildPlanRetryPhase(issue, project, slug, event.feedback(), event.retryCount()),
                    job.getId());

            String prUrl = runDoAndVerifyLoop(
                    project.getLocalPath().value(), slug, branchName,
                    planResult.sessionId(), job, event.feedback(), event.retryCount());

            job.complete(prUrl);
            agentJobRepository.save(job);
            publishStatus(event.issueId().value(), IssueStatus.IN_REVIEW);

        } catch (AgentExecutionException e) {
            job.fail("[DEVELOP-RETRY] " + e.getMessage());
            agentJobRepository.save(job);
            publishStatus(event.issueId().value(), IssueStatus.FAILED);
        }
    }

    // ─────────────────────────────────────────────
    // Do → Analysis 핑퐁 루프
    // ─────────────────────────────────────────────

    private String runDoAndVerifyLoop(String workDir, String slug, String branchName,
                                       String planSessionId, AgentJob job,
                                       String initialFeedback, int baseRetryCount) {
        String currentSessionId = planSessionId;
        String analysisOutput = null;
        int maxRetry = agentProperties.getMaxRetry();

        for (int attempt = 1; attempt <= maxRetry + 1; attempt++) {
            job.startCoding();
            agentJobRepository.save(job);

            String doPrompt = (attempt == 1 && analysisOutput == null)
                    ? PromptBuilder.buildDoPhase(slug)
                    : PromptBuilder.buildDoRetryPhase(slug, analysisOutput, attempt);

            PhaseResult doResult = claudeAgentExecutor.resumePhase(
                    workDir, doPrompt, currentSessionId, job.getId());
            currentSessionId = doResult.sessionId();

            job.startVerifying();
            agentJobRepository.save(job);

            PhaseResult analysisResult = claudeAgentExecutor.resumePhase(
                    workDir, PromptBuilder.buildAnalysisPhase(slug), currentSessionId, job.getId());
            analysisOutput = analysisResult.output();
            currentSessionId = analysisResult.sessionId();

            int matchRate = GapAnalysisChecker.extractMatchRate(analysisOutput);
            log.info("[Agent] Analysis (attempt: {}, matchRate: {}%)", attempt, matchRate);

            if (GapAnalysisChecker.isPassed(analysisOutput)) {
                pullRequestService.push(workDir, branchName);
                return pullRequestService.createDraftPr(
                        workDir, "main", branchName, "feat/" + slug,
                        buildPrBody(slug, analysisOutput));
            }

            if (attempt > maxRetry) {
                throw new AgentExecutionException(
                        "Gap Analysis %d회 실패 (matchRate: %d%%)".formatted(attempt, matchRate));
            }
            log.warn("[Agent] Gap 실패 (attempt: {}/{}), 재시도", attempt, maxRetry + 1);
        }
        throw new AgentExecutionException("Do-Analysis 루프 비정상 종료");
    }

    // ─────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────

    private void publishStatus(java.util.UUID issueId, IssueStatus status) {
        eventPublisher.publishEvent(IssueStatusChangedEvent.of(issueId, status));
    }

    private com.example.worker.issue.event.model.IssueCreatedEvent toCreatedEvent(AgentPhaseRequestedEvent e) {
        return new com.example.worker.issue.event.model.IssueCreatedEvent(
                e.issueId(), e.issueNumber(), e.issueTitle(), e.issueDescription(),
                e.priority(), e.projectId(), e.projectLocalPath(), e.baseBranch(),
                java.time.Instant.now());
    }

    private String buildPrBody(String slug, String analysisOutput) {
        String summary = analysisOutput.length() > 2000
                ? analysisOutput.substring(0, 2000) + "\n...(truncated)"
                : analysisOutput;
        return """
                ## Feature: %s

                ## Gap Analysis 결과
                ```
                %s
                ```
                > 자동 생성된 Draft PR입니다. 리뷰 후 머지하세요.
                """.formatted(slug, summary);
    }
}
