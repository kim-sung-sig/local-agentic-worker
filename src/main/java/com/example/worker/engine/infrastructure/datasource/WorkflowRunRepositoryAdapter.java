package com.example.worker.engine.infrastructure.datasource;

import com.example.worker.engine.application.port.WorkflowRunRepository;
import com.example.worker.engine.domain.model.AttemptRecord;
import com.example.worker.engine.domain.model.WorkflowRun;
import com.example.worker.engine.domain.model.WorkflowRunId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class WorkflowRunRepositoryAdapter implements WorkflowRunRepository {

    private final WorkflowRunJpaRepository jpaRepository;
    private final AttemptRecordJpaRepository attemptJpaRepository;

    WorkflowRunRepositoryAdapter(WorkflowRunJpaRepository jpaRepository,
                                 AttemptRecordJpaRepository attemptJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.attemptJpaRepository = attemptJpaRepository;
    }

    @Override
    public WorkflowRun save(WorkflowRun workflowRun) {
        WorkflowRunJpaEntity saved = jpaRepository.save(WorkflowRunJpaEntity.from(workflowRun));

        int persistedCount = attemptJpaRepository
                .findByWorkflowRunIdOrderByAttemptNumberAsc(saved.getId())
                .size();
        List<AttemptRecord> attempts = workflowRun.getAttempts();
        for (int i = persistedCount; i < attempts.size(); i++) {
            attemptJpaRepository.save(AttemptRecordJpaEntity.from(saved.getId(), attempts.get(i)));
        }

        return saved.toDomain(loadAttempts(saved.getId()));
    }

    @Override
    public Optional<WorkflowRun> findById(WorkflowRunId id) {
        return jpaRepository.findById(id.value())
                .map(entity -> entity.toDomain(loadAttempts(entity.getId())));
    }

    @Override
    public Optional<WorkflowRun> findByTemporalWorkflowId(String temporalWorkflowId) {
        return jpaRepository.findByTemporalWorkflowId(temporalWorkflowId)
                .map(entity -> entity.toDomain(loadAttempts(entity.getId())));
    }

    private List<AttemptRecord> loadAttempts(UUID workflowRunId) {
        return attemptJpaRepository.findByWorkflowRunIdOrderByAttemptNumberAsc(workflowRunId).stream()
                .map(AttemptRecordJpaEntity::toDomain)
                .toList();
    }
}
