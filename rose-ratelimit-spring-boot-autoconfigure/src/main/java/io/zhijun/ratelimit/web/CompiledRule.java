package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import java.util.Set;
import org.springframework.web.util.pattern.PathPattern;

/** Validated startup snapshot; no mutable configuration objects cross into request processing. */
public record CompiledRule(
        String id,
        boolean enabled,
        Stage stage,
        PathPattern path,
        Set<String> methods,
        String handler,
        Scope scope,
        RateLimitPolicy policy,
        RateLimitStore store,
        RateLimitKeyResolver resolver,
        String keyHeader,
        MissingKey missingKey,
        FailureMode failureMode,
        int status,
        String contentType,
        String body,
        ResilienceRuleKind kind,
        CircuitBreakerSpec circuitBreaker,
        int circuitOpenStatus,
        RetrySpec retry,
        TimeLimiterSpec timeLimiter,
        BulkheadSpec bulkhead) {

    public CompiledRule {
        if (kind == null) kind = ResilienceRuleKind.RATE_LIMIT;
        if (circuitOpenStatus < 400 || circuitOpenStatus > 599) {
            throw new IllegalArgumentException("circuit-open-status must be an HTTP error status");
        }
    }

    public boolean usesCircuitBreaker() {
        return kind == ResilienceRuleKind.CIRCUIT_BREAKER
                || (kind == ResilienceRuleKind.COMPOSITE && circuitBreaker != null);
    }

    public boolean usesRetry() {
        return retry != null;
    }

    public boolean usesTimeLimiter() {
        return timeLimiter != null;
    }

    public boolean usesBulkhead() {
        return bulkhead != null;
    }

    public boolean usesBusinessDecoration() {
        return usesRetry() || usesTimeLimiter() || usesBulkhead();
    }
}
