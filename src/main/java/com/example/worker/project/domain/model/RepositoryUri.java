package com.example.worker.project.domain.model;

import com.example.worker.common.exception.BusinessException;
import com.example.worker.common.exception.ErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public record RepositoryUri(String value) {

    private static final Set<String> REMOTE_GIT_SCHEMES = Set.of("https", "http", "ssh");

    public RepositoryUri {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null
                    || !REMOTE_GIT_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.PROJECT_REPOSITORY_URI_INVALID);
            }
        } catch (URISyntaxException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.PROJECT_REPOSITORY_URI_INVALID);
        }
    }
}
