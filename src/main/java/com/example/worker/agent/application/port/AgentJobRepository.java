package com.example.worker.agent.application.port;

import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentPhase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentJobRepository {

    AgentJob save(AgentJob agentJob);

    Optional<AgentJob> findById(AgentJobId id);

    List<AgentJob> findByIssueId(UUID issueId);

    /** 특정 이슈의 특정 페이즈 중 가장 최근 성공 Job (세션 ID 이어받기용) */
    Optional<AgentJob> findLatestSucceededByIssueIdAndPhase(UUID issueId, AgentPhase phase);
}
