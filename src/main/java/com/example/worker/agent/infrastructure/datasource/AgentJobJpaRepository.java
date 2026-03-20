package com.example.worker.agent.infrastructure.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AgentJobJpaRepository extends JpaRepository<AgentJobJpaEntity, UUID> {

    List<AgentJobJpaEntity> findByIssueId(UUID issueId);
}
