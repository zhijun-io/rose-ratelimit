package io.zhijun.ratelimit;

/** Ordered behaviors for {@link ResilienceRuleKind#COMPOSITE} (default order: rate-limit then circuit-breaker). */
public enum ResilienceBehavior {
    RATE_LIMIT,
    CIRCUIT_BREAKER,
    RETRY,
    TIME_LIMITER,
    BULKHEAD
}
