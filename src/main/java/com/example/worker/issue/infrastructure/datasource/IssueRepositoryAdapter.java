package com.example.worker.issue.infrastructure.datasource;

import com.example.worker.issue.application.port.IssueRepository;
import com.example.worker.issue.domain.model.Issue;
import com.example.worker.issue.domain.model.IssueId;
import com.example.worker.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class IssueRepositoryAdapter implements IssueRepository {

    private final IssueJpaRepository jpaRepository;

    public IssueRepositoryAdapter(IssueJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Issue save(Issue issue) {
        return jpaRepository.save(IssueJpaEntity.from(issue)).toDomain();
    }

    @Override
    public Optional<Issue> findById(IssueId id) {
        return jpaRepository.findById(id.value()).map(IssueJpaEntity::toDomain);
    }

    @Override
    public List<Issue> findByProjectId(ProjectId projectId) {
        return jpaRepository.findByProjectId(projectId.value()).stream()
                .map(IssueJpaEntity::toDomain)
                .toList();
    }

    @Override
    public int findMaxIssueNumber(ProjectId projectId) {
        return jpaRepository.findMaxIssueNumber(projectId.value());
    }
}
