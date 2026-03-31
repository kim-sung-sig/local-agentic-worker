package com.example.worker.agent.infrastructure.datasource;

import com.example.worker.agent.domain.model.AgentJobStatus;
import com.example.worker.agent.domain.model.AgentPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AgentJobJpaRepository extends JpaRepository<AgentJobJpaEntity, UUID> {

    List<AgentJobJpaEntity> findByIssueId(UUID issueId);

    Optional<AgentJobJpaEntity> findTopByIssueIdAndPhaseAndStatusOrderByStartedAtDesc(
            UUID issueId, AgentPhase phase, AgentJobStatus status);
}
