package io.zhijun.ratelimit;

import java.time.Duration;

/** Immutable time-limiter parameters (maps to Resilience4j in optional module). */
public record TimeLimiterSpec(Duration timeoutDuration, boolean cancelRunningFuture) {
    public TimeLimiterSpec {
        if (timeoutDuration == null || timeoutDuration.isNegative() || timeoutDuration.isZero()) {
            throw new IllegalArgumentException("time-limiter timeout-duration must be positive");
        }
    }
}
