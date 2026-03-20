package com.example.worker.issue.infrastructure.datasource;

import com.example.worker.issue.domain.model.*;
import com.example.worker.project.domain.model.ProjectId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "issue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueJpaEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "issue_number", nullable = false)
    private int issueNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private IssueJpaEntity(UUID id, UUID projectId, int issueNumber,
                           String title, String description,
                           Priority priority, IssueStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.issueNumber = issueNumber;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static IssueJpaEntity from(Issue domain) {
        return new IssueJpaEntity(
                domain.getId().value(),
                domain.getProjectId().value(),
                domain.getIssueNumber().value(),
                domain.getTitle(),
                domain.getDescription(),
                domain.getPriority(),
                domain.getStatus(),
                domain.getCreatedAt()
        );
    }

    public Issue toDomain() {
        return Issue.reconstitute(
                IssueId.of(id),
                ProjectId.of(projectId),
                new IssueNumber(issueNumber),
                title,
                description,
                priority,
                status,
                createdAt
        );
    }
}
