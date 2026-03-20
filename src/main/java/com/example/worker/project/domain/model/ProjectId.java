package com.example.worker.project.domain.model;

import java.util.UUID;

public record ProjectId(UUID value) {

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }

    public static ProjectId of(UUID value) {
        return new ProjectId(value);
    }
}
