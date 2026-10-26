package io.zhijun.ratelimit;

/** Inputs for one rate-limit attempt after identity and storage key are resolved. */
public record RoseResilienceRequest(
        String storageKey,
        RateLimitPolicy policy,
        RateLimitStore store,
        MissingKeyPolicy missingKeyPolicy,
        String resolvedIdentity,
        FailureMode failureMode) {
    public enum MissingKeyPolicy {
        REJECT,
        SHARED
    }

    public enum FailureMode {
        OPEN,
        CLOSED
    }
}
