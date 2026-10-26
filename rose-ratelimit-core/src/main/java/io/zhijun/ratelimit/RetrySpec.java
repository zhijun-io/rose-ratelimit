package io.zhijun.ratelimit;

import java.time.Duration;

/** Immutable retry parameters (maps to Resilience4j in optional module). */
public record RetrySpec(int maxAttempts, Duration waitDuration) {
    public RetrySpec {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("retry max-attempts must be at least 1");
        }
        if (waitDuration == null || waitDuration.isNegative() || waitDuration.isZero()) {
            throw new IllegalArgumentException("retry wait-duration must be positive");
        }
    }
}
