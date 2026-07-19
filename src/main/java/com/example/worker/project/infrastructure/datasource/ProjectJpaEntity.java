package com.example.worker.project.infrastructure.datasource;

import com.example.worker.project.domain.model.Project;
import com.example.worker.project.domain.model.ProjectId;
import com.example.worker.project.domain.model.RemoteProjectRegistration;
import com.example.worker.project.domain.model.RepositoryUri;
import com.example.worker.project.domain.model.BranchName;
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

    @Column(name = "local_path")
    private String localPath;

    @Column(name = "repository_uri")
    private String repositoryUri;

    @Column(name = "credential_ref")
    private String credentialRef;

    @Column(name = "base_branch", nullable = false)
    private String baseBranch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ProjectJpaEntity from(Project domain) {
        ProjectJpaEntity entity = new ProjectJpaEntity();
        entity.id = domain.getId().value();
        entity.name = domain.getName();
        entity.localPath = domain.getLocalPath() == null ? null : domain.getLocalPath().value();
        entity.repositoryUri = domain.getRepositoryUri() == null ? null : domain.getRepositoryUri().value();
        entity.credentialRef = domain.getCredentialRef();
        entity.baseBranch = domain.getBaseBranch().value();
        entity.createdAt = domain.getCreatedAt();
        return entity;
    }

    public Project toDomain() {
        if (repositoryUri != null) {
            return Project.reconstituteRemote(
                    ProjectId.of(id),
                    new RemoteProjectRegistration(name, new RepositoryUri(repositoryUri),
                            BranchName.of(baseBranch), credentialRef),
                    createdAt
            );
        }
        return Project.reconstitute(
                ProjectId.of(id),
                name,
                localPath,
                baseBranch,
                createdAt
        );
    }
}
