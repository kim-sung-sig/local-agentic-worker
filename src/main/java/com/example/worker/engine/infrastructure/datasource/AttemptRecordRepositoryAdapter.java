package com.example.worker.engine.infrastructure.datasource;

import com.example.worker.engine.application.port.AttemptRecordRepository;
import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.WorkflowRunId;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class AttemptRecordRepositoryAdapter implements AttemptRecordRepository {

    private final AttemptRecordJpaRepository jpaRepository;

    AttemptRecordRepositoryAdapter(AttemptRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<AttemptRecord> findByWorkflowRunId(WorkflowRunId workflowRunId) {
        return jpaRepository.findByWorkflowRunIdOrderByAttemptNumberAsc(workflowRunId.value()).stream()
                .map(AttemptRecordJpaEntity::toDomain)
                .toList();
    }
}
