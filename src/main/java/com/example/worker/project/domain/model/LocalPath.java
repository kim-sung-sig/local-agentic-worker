package com.example.worker.project.domain.model;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;

import java.nio.file.Files;
import java.nio.file.Path;

public record LocalPath(String value) {

    public LocalPath {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PROJECT_PATH_INVALID);
        }
        if (!Files.isDirectory(Path.of(value))) {
            throw new BusinessException(ErrorCode.PROJECT_PATH_INVALID);
        }
    }
}
