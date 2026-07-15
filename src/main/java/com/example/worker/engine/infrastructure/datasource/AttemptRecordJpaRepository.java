package com.example.worker.engine.infrastructure.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AttemptRecordJpaRepository extends JpaRepository<AttemptRecordJpaEntity, UUID> {

    List<AttemptRecordJpaEntity> findByWorkflowRunIdOrderByAttemptNumberAsc(UUID workflowRunId);
}
