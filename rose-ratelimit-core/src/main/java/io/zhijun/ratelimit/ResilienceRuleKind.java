package io.zhijun.ratelimit;

/** v6 rule behavior classification; {@link #RATE_LIMIT} preserves v5 bucket-only semantics. */
public enum ResilienceRuleKind {
    RATE_LIMIT,
    CIRCUIT_BREAKER,
    COMPOSITE
}
