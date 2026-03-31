package com.example.worker.agent.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class AgentJob {

    private final AgentJobId id;
    private final UUID issueId;
    private final UUID projectId;
    private final String branchName;
    private final AgentPhase phase;
    private AgentJobStatus status;
    private final Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
    private String prUrl;
    private String documentPath;    // PLAN/DESIGN 페이즈 산출물 경로
    private String claudeSessionId; // Claude CLI --resume 용 세션 ID

    private AgentJob(AgentJobId id, UUID issueId, UUID projectId, String branchName,
                     AgentPhase phase, AgentJobStatus status, Instant startedAt,
                     Instant finishedAt, String errorMessage, String prUrl,
                     String documentPath, String claudeSessionId) {
        this.id = id;
        this.issueId = issueId;
        this.projectId = projectId;
        this.branchName = branchName;
        this.phase = phase;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
        this.prUrl = prUrl;
        this.documentPath = documentPath;
        this.claudeSessionId = claudeSessionId;
    }

    public static AgentJob create(UUID issueId, UUID projectId, String branchName, AgentPhase phase) {
        return new AgentJob(AgentJobId.newId(), issueId, projectId, branchName,
                phase, AgentJobStatus.PENDING, Instant.now(), null, null, null, null, null);
    }

    /** 하위 호환 — DEVELOP 페이즈 기본값 */
    public static AgentJob create(UUID issueId, UUID projectId, String branchName) {
        return create(issueId, projectId, branchName, AgentPhase.DEVELOP);
    }

    public static AgentJob reconstitute(AgentJobId id, UUID issueId, UUID projectId,
                                        String branchName, AgentPhase phase, AgentJobStatus status,
                                        Instant startedAt, Instant finishedAt,
                                        String errorMessage, String prUrl,
                                        String documentPath, String claudeSessionId) {
        return new AgentJob(id, issueId, projectId, branchName, phase, status,
                startedAt, finishedAt, errorMessage, prUrl, documentPath, claudeSessionId);
    }

    /** 하위 호환 */
    public static AgentJob reconstitute(AgentJobId id, UUID issueId, UUID projectId,
                                        String branchName, AgentPhase phase, AgentJobStatus status,
                                        Instant startedAt, Instant finishedAt,
                                        String errorMessage, String prUrl, String documentPath) {
        return reconstitute(id, issueId, projectId, branchName, phase, status,
                startedAt, finishedAt, errorMessage, prUrl, documentPath, null);
    }

    /** 하위 호환 */
    public static AgentJob reconstitute(AgentJobId id, UUID issueId, UUID projectId,
                                        String branchName, AgentJobStatus status,
                                        Instant startedAt, Instant finishedAt,
                                        String errorMessage, String prUrl) {
        return reconstitute(id, issueId, projectId, branchName, AgentPhase.DEVELOP, status,
                startedAt, finishedAt, errorMessage, prUrl, null, null);
    }

    public void startPlanning() { this.status = AgentJobStatus.PLANNING; }
    public void startCoding()   { this.status = AgentJobStatus.CODING; }
    public void startVerifying(){ this.status = AgentJobStatus.VERIFYING; }

    /** Claude CLI 세션 ID 기록 — 다음 페이즈에서 --resume으로 이어받기 위함 */
    public void recordSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            this.claudeSessionId = sessionId;
        }
    }

    /** PLAN / DESIGN 페이즈 완료 — 문서 경로 + 세션 ID 기록 */
    public void completeWithDocument(String documentPath, String sessionId) {
        this.status = AgentJobStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.documentPath = documentPath;
        recordSessionId(sessionId);
    }

    /** 하위 호환 */
    public void completeWithDocument(String documentPath) {
        completeWithDocument(documentPath, null);
    }

    /** DEVELOP 페이즈 완료 — PR URL + 세션 ID 기록 */
    public void complete(String prUrl, String sessionId) {
        this.status = AgentJobStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.prUrl = prUrl;
        recordSessionId(sessionId);
    }

    /** 하위 호환 */
    public void complete(String prUrl) {
        complete(prUrl, null);
    }

    public void fail(String errorMessage) {
        this.status = AgentJobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}
