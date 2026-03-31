package com.example.worker.agent.event.model;

import com.example.worker.agent.domain.model.AgentPhase;

import java.util.UUID;

/**
 * 특정 페이즈 실행을 요청하는 이벤트.
 * API에서 발행 → AgentWorkerService가 수신하여 Claude 실행.
 */
public record AgentPhaseRequestedEvent(
        UUID issueId,
        AgentPhase phase,
        UUID projectId,
        String projectLocalPath,
        String baseBranch,
        int issueNumber,
        String issueTitle,
        String issueDescription,
        String priority
) {}
