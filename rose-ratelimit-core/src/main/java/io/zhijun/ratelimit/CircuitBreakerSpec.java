package io.zhijun.ratelimit;

import java.time.Duration;

/** Immutable circuit breaker parameters (maps to Resilience4j in optional module). */
public record CircuitBreakerSpec(
        float failureRateThreshold,
        float slowCallRateThreshold,
        Duration slowCallDurationThreshold,
        int minimumNumberOfCalls,
        int slidingWindowSize,
        Duration waitDurationInOpenState,
        int permittedNumberOfCallsInHalfOpenState) {
    public CircuitBreakerSpec {
        if (minimumNumberOfCalls < 1 || slidingWindowSize < 1 || permittedNumberOfCallsInHalfOpenState < 1) {
            throw new IllegalArgumentException("circuit breaker call/window counts must be positive");
        }
        if (failureRateThreshold < 0
                || failureRateThreshold > 100
                || slowCallRateThreshold < 0
                || slowCallRateThreshold > 100) {
            throw new IllegalArgumentException("circuit breaker rate thresholds must be 0..100");
        }
    }
}
