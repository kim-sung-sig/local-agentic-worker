package com.example.worker.engine.application.service;

import com.example.worker.engine.application.contract.v1.AttemptPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttemptPolicyResolver")
class AttemptPolicyResolverTest {

    private final AttemptPolicyResolver resolver = new AttemptPolicyResolver();

    @Test
    @DisplayName("미설정(0,0)이면 기본값 maxAttempts=2, minimumQaScore=90을 적용한다")
    void resolve_unset_appliesDefaults() {
        AttemptPolicy resolved = resolver.resolve(new AttemptPolicy(0, 0, 1));

        assertThat(resolved.maxAttempts()).isEqualTo(2);
        assertThat(resolved.minimumQaScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("티켓 정책이 오버라이드한 값을 그대로 반영한다")
    void resolve_ticketOverride_isRespected() {
        AttemptPolicy resolved = resolver.resolve(new AttemptPolicy(5, 95, 1));

        assertThat(resolved.maxAttempts()).isEqualTo(5);
        assertThat(resolved.minimumQaScore()).isEqualTo(95);
    }

    @Test
    @DisplayName("maxAttempts가 10을 초과하면 거부된다")
    void resolve_maxAttemptsAboveTen_isRejected() {
        assertThatThrownBy(() -> resolver.resolve(new AttemptPolicy(11, 90, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 maxAttempts는 미설정으로 간주해 기본값을 적용한다")
    void resolve_negativeMaxAttempts_treatedAsUnset() {
        AttemptPolicy resolved = resolver.resolve(new AttemptPolicy(-1, 90, 1));

        assertThat(resolved.maxAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("minimumQaScore가 100을 초과하면 거부된다")
    void resolve_minimumQaScoreAboveHundred_isRejected() {
        assertThatThrownBy(() -> resolver.resolve(new AttemptPolicy(2, 101, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxAttempts 하한 1은 허용된다")
    void resolve_maxAttemptsAtLowerBound_isAccepted() {
        AttemptPolicy resolved = resolver.resolve(new AttemptPolicy(1, 90, 1));

        assertThat(resolved.maxAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("maxAttempts 상한 10은 허용된다")
    void resolve_maxAttemptsAtUpperBound_isAccepted() {
        AttemptPolicy resolved = resolver.resolve(new AttemptPolicy(10, 90, 1));

        assertThat(resolved.maxAttempts()).isEqualTo(10);
    }
}
