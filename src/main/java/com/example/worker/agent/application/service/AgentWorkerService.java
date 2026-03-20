package com.example.worker.agent.application.service;

import com.example.worker.agent.application.exception.AgentExecutionException;
import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.event.model.IssueStatusChangedEvent;
import com.example.worker.issue.event.model.IssueCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkerService {

    private static final Logger log = LoggerFactory.getLogger(AgentWorkerService.class);

    private final AgentJobRepository agentJobRepository;
    private final GitBranchService gitBranchService;
    private final ClaudeAgentExecutor claudeAgentExecutor;
    private final PullRequestService pullRequestService;
    private final ApplicationEventPublisher eventPublisher;

    public AgentWorkerService(AgentJobRepository agentJobRepository,
                               GitBranchService gitBranchService,
                               ClaudeAgentExecutor claudeAgentExecutor,
                               PullRequestService pullRequestService,
                               ApplicationEventPublisher eventPublisher) {
        this.agentJobRepository = agentJobRepository;
        this.gitBranchService = gitBranchService;
        this.claudeAgentExecutor = claudeAgentExecutor;
        this.pullRequestService = pullRequestService;
        this.eventPublisher = eventPublisher;
    }

    public void handle(IssueCreatedEvent event) {
        String branchName = buildBranchName(event.issueNumber(), event.title());
        AgentJob job = agentJobRepository.save(
                AgentJob.create(event.issueId(), event.projectId(), branchName));

        eventPublisher.publishEvent(IssueStatusChangedEvent.of(event.issueId(), "IN_PROGRESS"));

        try {
            job.start();
            agentJobRepository.save(job);

            gitBranchService.createBranch(event.projectLocalPath(), event.baseBranch(), branchName);

            String prompt = PromptBuilder.build(event);
            String claudeOutput = claudeAgentExecutor.execute(event.projectLocalPath(), prompt);

            pullRequestService.push(event.projectLocalPath(), branchName);
            String prTitle = "feat: #%d %s".formatted(event.issueNumber(), event.title());
            String prBody = buildPrBody(event, claudeOutput);
            String prUrl = pullRequestService.createDraftPr(
                    event.projectLocalPath(), event.baseBranch(), branchName, prTitle, prBody);

            job.complete(prUrl);
            agentJobRepository.save(job);
            eventPublisher.publishEvent(IssueStatusChangedEvent.of(event.issueId(), "IN_REVIEW"));

            log.info("[AgentWorker] 완료 이슈 #{}: {}", event.issueNumber(), prUrl);

        } catch (AgentExecutionException e) {
            log.error("[AgentWorker] 실패 이슈 #{}: {}", event.issueNumber(), e.getMessage());
            job.fail(e.getMessage());
            agentJobRepository.save(job);
            eventPublisher.publishEvent(IssueStatusChangedEvent.of(event.issueId(), "FAILED"));
        }
    }

    private String buildBranchName(int issueNumber, String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        if (slug.length() > 40) slug = slug.substring(0, 40);
        return "feat/issue-%d-%s".formatted(issueNumber, slug);
    }

    private String buildPrBody(IssueCreatedEvent event, String claudeOutput) {
        String summary = claudeOutput.length() > 2000
                ? claudeOutput.substring(0, 2000) + "\n...(truncated)"
                : claudeOutput;
        return """
                ## 이슈 #%d — %s

                > 우선순위: %s

                %s

                ---

                ## Claude 작업 요약

                ```
                %s
                ```

                > 자동 생성된 Draft PR입니다. 리뷰 후 머지하세요.
                """.formatted(
                event.issueNumber(), event.title(),
                event.priority(),
                event.description() == null ? "" : event.description(),
                summary
        );
    }
}
