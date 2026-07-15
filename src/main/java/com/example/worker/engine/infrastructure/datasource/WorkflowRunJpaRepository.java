package com.example.worker.engine.infrastructure.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface WorkflowRunJpaRepository extends JpaRepository<WorkflowRunJpaEntity, UUID> {

    Optional<WorkflowRunJpaEntity> findByTemporalWorkflowId(String temporalWorkflowId);
}
