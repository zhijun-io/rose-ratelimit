package io.zhijun.ratelimit;

import java.time.Duration;

/** Transport-agnostic result of one resilience decision (v6-R0: rate-limit only). */
public record RoseResilienceOutcome(Result result, Duration retryAfter) {
    public enum Result {
        ALLOWED,
        REJECTED,
        MISSING_IDENTITY,
        BACKEND_OPEN,
        BACKEND_CLOSED,
        CIRCUIT_OPEN
    }

    public static RoseResilienceOutcome allowed() {
        return new RoseResilienceOutcome(Result.ALLOWED, Duration.ZERO);
    }

    public static RoseResilienceOutcome rejected(Duration retryAfter) {
        return new RoseResilienceOutcome(Result.REJECTED, retryAfter);
    }

    public static RoseResilienceOutcome missingIdentity() {
        return new RoseResilienceOutcome(Result.MISSING_IDENTITY, Duration.ZERO);
    }

    public static RoseResilienceOutcome backendOpen() {
        return new RoseResilienceOutcome(Result.BACKEND_OPEN, Duration.ZERO);
    }

    public static RoseResilienceOutcome backendClosed() {
        return new RoseResilienceOutcome(Result.BACKEND_CLOSED, Duration.ZERO);
    }

    public static RoseResilienceOutcome circuitOpen() {
        return new RoseResilienceOutcome(Result.CIRCUIT_OPEN, Duration.ZERO);
    }
}
