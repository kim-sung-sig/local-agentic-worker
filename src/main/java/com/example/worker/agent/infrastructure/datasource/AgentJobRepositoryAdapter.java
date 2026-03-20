package com.example.worker.agent.infrastructure.datasource;

import com.example.worker.agent.application.port.AgentJobRepository;
import com.example.worker.agent.domain.model.AgentJob;
import com.example.worker.agent.domain.model.AgentJobId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AgentJobRepositoryAdapter implements AgentJobRepository {

    private final AgentJobJpaRepository jpaRepository;

    AgentJobRepositoryAdapter(AgentJobJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AgentJob save(AgentJob agentJob) {
        return jpaRepository.save(AgentJobJpaEntity.from(agentJob)).toDomain();
    }

    @Override
    public Optional<AgentJob> findById(AgentJobId id) {
        return jpaRepository.findById(id.value()).map(AgentJobJpaEntity::toDomain);
    }

    @Override
    public List<AgentJob> findByIssueId(UUID issueId) {
        return jpaRepository.findByIssueId(issueId).stream()
                .map(AgentJobJpaEntity::toDomain)
                .toList();
    }
}
