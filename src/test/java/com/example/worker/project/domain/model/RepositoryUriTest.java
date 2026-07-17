package com.example.worker.project.domain.model;

import com.example.worker.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RepositoryUri")
class RepositoryUriTest {

    @Nested
    @DisplayName("원격 Git 저장소 URI 생성")
    class Create {

        @Test
        @DisplayName("HTTPS 저장소 URI를 보존한다")
        void preservesHttpsRepositoryUri() {
            // Given
            String value = "https://github.com/acme/catalog.git";

            // When
            RepositoryUri repositoryUri = new RepositoryUri(value);

            // Then
            assertEquals(value, repositoryUri.value());
        }

        @Test
        @DisplayName("SSH 저장소 URI를 보존한다")
        void preservesSshRepositoryUri() {
            // Given
            String value = "ssh://git@github.com/acme/catalog.git";

            // When
            RepositoryUri repositoryUri = new RepositoryUri(value);

            // Then
            assertEquals(value, repositoryUri.value());
        }

        @Test
        @DisplayName("로컬 파일 URI는 거부한다")
        void rejectsLocalFileUri() {
            // Given
            String value = "file:///workspace/catalog";

            // When / Then
            assertThrows(BusinessException.class, () -> new RepositoryUri(value));
        }

        @Test
        @DisplayName("지원하지 않는 URI 스킴은 거부한다")
        void rejectsUnsupportedUriScheme() {
            // Given
            String value = "ftp://github.com/acme/catalog.git";

            // When / Then
            assertThrows(BusinessException.class, () -> new RepositoryUri(value));
        }

        @Test
        @DisplayName("사용자 정보가 포함된 저장소 URI는 거부한다")
        void rejectsRepositoryUriWithUserInfo() {
            // Given
            String value = "https://token:secret@github.com/acme/catalog.git";

            // When / Then
            assertThrows(BusinessException.class, () -> new RepositoryUri(value));
        }

        @Test
        @DisplayName("호스트가 없는 저장소 URI는 거부한다")
        void rejectsRepositoryUriWithoutHost() {
            // Given
            String value = "https:catalog.git";

            // When / Then
            assertThrows(BusinessException.class, () -> new RepositoryUri(value));
        }

        @Test
        @DisplayName("query 또는 fragment가 포함된 저장소 URI는 거부한다")
        void rejectsRepositoryUriWithQueryOrFragment() {
            // Given
            String queryValue = "https://github.com/acme/catalog.git?access_token=secret";
            String fragmentValue = "https://github.com/acme/catalog.git#secret";

            // When / Then
            assertThrows(BusinessException.class, () -> new RepositoryUri(queryValue));
            assertThrows(BusinessException.class, () -> new RepositoryUri(fragmentValue));
        }
    }
}
