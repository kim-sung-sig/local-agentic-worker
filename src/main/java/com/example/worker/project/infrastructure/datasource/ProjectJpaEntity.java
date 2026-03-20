package com.example.worker.project.infrastructure.datasource;

import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "local_path", nullable = false, unique = true)
    private String localPath;

    @Column(name = "base_branch", nullable = false)
    private String baseBranch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private ProjectJpaEntity(UUID id, String name, String localPath, String baseBranch, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.localPath = localPath;
        this.baseBranch = baseBranch;
        this.createdAt = createdAt;
    }

    public static ProjectJpaEntity from(Project domain) {
        return new ProjectJpaEntity(
                domain.getId().value(),
                domain.getName(),
                domain.getLocalPath().value(),
                domain.getBaseBranch().value(),
                domain.getCreatedAt()
        );
    }

    public Project toDomain() {
        return Project.reconstitute(
                ProjectId.of(id),
                name,
                localPath,
                baseBranch,
                createdAt
        );
    }
}
