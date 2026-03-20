package com.example.worker.issue.infrastructure.datasource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface IssueJpaRepository extends JpaRepository<IssueJpaEntity, UUID> {

    List<IssueJpaEntity> findByProjectId(UUID projectId);

    @Query("SELECT COALESCE(MAX(i.issueNumber), 0) FROM IssueJpaEntity i WHERE i.projectId = :projectId")
    int findMaxIssueNumber(@Param("projectId") UUID projectId);
}
