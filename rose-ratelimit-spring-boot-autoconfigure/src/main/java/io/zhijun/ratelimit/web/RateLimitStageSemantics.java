package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.RoseResilienceOutcome;
import io.zhijun.ratelimit.RoseResilienceRequest;
import io.zhijun.ratelimit.web.RateLimitOptions.FailureMode;
import io.zhijun.ratelimit.web.RateLimitOptions.MissingKey;

/** Per-stage context, outcome mapping, and rejection for {@link RateLimitGuardedInvocation}. */
public interface RateLimitStageSemantics {
    default void enter(String selector) {}

    default void exit() {}

    default String resolveIdentity(CompiledRule rule, String selector) {
        return rule.resolver().resolve(null, rule.keyHeader());
    }

    default RoseResilienceRequest.MissingKeyPolicy missingKey(MissingKey missingKey) {
        return missingKey == MissingKey.REJECT
                ? RoseResilienceRequest.MissingKeyPolicy.REJECT
                : RoseResilienceRequest.MissingKeyPolicy.SHARED;
    }

    default RoseResilienceRequest.FailureMode failureMode(FailureMode failureMode) {
        return failureMode == FailureMode.OPEN
                ? RoseResilienceRequest.FailureMode.OPEN
                : RoseResilienceRequest.FailureMode.CLOSED;
    }

    default StageGuardDecision map(RoseResilienceOutcome outcome, CompiledRule rule) {
        return switch (outcome.result()) {
            case ALLOWED -> new StageGuardDecision(0, "", "allowed");
            case REJECTED -> new StageGuardDecision(rule.status(), rule.body(), "rejected", outcome.retryAfter());
            case MISSING_IDENTITY ->
                new StageGuardDecision(
                        400, "{\"title\":\"Rate limit identity is missing\",\"status\":400}", "missing_key");
            case BACKEND_OPEN -> new StageGuardDecision(0, "", "backend_error_open");
            case BACKEND_CLOSED ->
                new StageGuardDecision(
                        503, "{\"title\":\"Rate limit service unavailable\",\"status\":503}", "backend_error_closed");
            case CIRCUIT_OPEN ->
                new StageGuardDecision(
                        rule.circuitOpenStatus(),
                        "{\"title\":\"Circuit breaker is open\",\"status\":" + rule.circuitOpenStatus() + "}",
                        "circuit_open");
        };
    }

    Exception reject(StageGuardDecision decision, CompiledRule rule);
}
