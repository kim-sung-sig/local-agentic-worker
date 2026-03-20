package com.example.worker.project.domain.model;

import java.util.Objects;

public record BranchName(String value) {

    public BranchName {
        Objects.requireNonNull(value, "브랜치명은 필수입니다.");
        if (value.isBlank()) throw new IllegalArgumentException("브랜치명은 비어있을 수 없습니다.");
    }

    public static BranchName of(String value) {
        return new BranchName(value);
    }
}
