package com.example.worker.project.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Project {

    private final ProjectId id;
    private final String name;
    private final LocalPath localPath;
    private final RepositoryUri repositoryUri;
    private final BranchName baseBranch;
    private final String credentialRef;
    private final LocalDateTime createdAt;

    private Project(ProjectId id, String name, LocalPath localPath, RepositoryUri repositoryUri,
                    BranchName baseBranch, String credentialRef, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.localPath = localPath;
        this.repositoryUri = repositoryUri;
        this.baseBranch = baseBranch;
        this.credentialRef = credentialRef;
        this.createdAt = createdAt;
    }

    public static Project create(String name, String localPath, String baseBranch) {
        return new Project(
                ProjectId.newId(),
                name,
                new LocalPath(localPath),
                null,
                BranchName.of(baseBranch),
                null,
                LocalDateTime.now()
        );
    }

    public static Project createRemote(RemoteProjectRegistration registration) {
        return new Project(
                ProjectId.newId(),
                registration.name(),
                null,
                registration.repositoryUri(),
                registration.baseBranch(),
                registration.credentialRef(),
                LocalDateTime.now()
        );
    }

    public static Project reconstitute(ProjectId id, String name, String localPath, String baseBranch, LocalDateTime createdAt) {
        return new Project(id, name, new LocalPath(localPath), null, BranchName.of(baseBranch), null, createdAt);
    }

    public static Project reconstituteRemote(ProjectId id, RemoteProjectRegistration registration,
                                              LocalDateTime createdAt) {
        return new Project(
                id,
                registration.name(),
                null,
                registration.repositoryUri(),
                registration.baseBranch(),
                registration.credentialRef(),
                createdAt
        );
    }

}
