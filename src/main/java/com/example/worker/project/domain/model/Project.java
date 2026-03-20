package com.example.worker.project.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Project {

    private final ProjectId id;
    private final String name;
    private final LocalPath localPath;
    private final BranchName baseBranch;
    private final LocalDateTime createdAt;

    private Project(ProjectId id, String name, LocalPath localPath, BranchName baseBranch, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.localPath = localPath;
        this.baseBranch = baseBranch;
        this.createdAt = createdAt;
    }

    public static Project create(String name, String localPath, String baseBranch) {
        return new Project(
                ProjectId.newId(),
                name,
                new LocalPath(localPath),
                BranchName.of(baseBranch),
                LocalDateTime.now()
        );
    }

    public static Project reconstitute(ProjectId id, String name, String localPath, String baseBranch, LocalDateTime createdAt) {
        return new Project(id, name, new LocalPath(localPath), BranchName.of(baseBranch), createdAt);
    }

}
