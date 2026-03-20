package com.example.worker.agent.application.port;

import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentJobRepository {

    AgentJob save(AgentJob agentJob);

    Optional<AgentJob> findById(AgentJobId id);

    List<AgentJob> findByIssueId(UUID issueId);
}
