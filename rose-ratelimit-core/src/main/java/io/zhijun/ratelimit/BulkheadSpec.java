package io.zhijun.ratelimit;

import java.time.Duration;

/** Immutable bulkhead parameters (maps to Resilience4j in optional module). */
public record BulkheadSpec(int maxConcurrentCalls, Duration maxWaitDuration) {
    public BulkheadSpec {
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("bulkhead max-concurrent-calls must be at least 1");
        }
        if (maxWaitDuration == null || maxWaitDuration.isNegative()) {
            throw new IllegalArgumentException("bulkhead max-wait-duration must not be negative");
        }
    }
}
