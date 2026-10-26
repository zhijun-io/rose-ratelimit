package io.zhijun.ratelimit;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPolicyAndKeysTest {
    @Test
    void invalidPeriodsAreRejectedBeforeStorage() {
        assertThatThrownBy(() -> new RateLimitPolicy(1, 1, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(1, 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysSeparateNamespaceAndUnambiguousIdentityComponents() {
        assertThat(RateLimitKeys.of("app1", "rule", "client")).isNotEqualTo(RateLimitKeys.of("app2", "rule", "client"));
        assertThat(RateLimitKeys.of("a", "bc")).isNotEqualTo(RateLimitKeys.of("ab", "c"));
        assertThat(RateLimitKeys.of("rule", "missing", ""))
                .isNotEqualTo(RateLimitKeys.of("rule", "present", "missing"));
    }
}
