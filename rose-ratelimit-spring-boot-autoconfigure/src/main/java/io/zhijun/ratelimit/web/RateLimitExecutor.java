package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.web.RateLimitOptions.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** HTTP stage: per-request deduplication and servlet response mapping. */
public final class RateLimitExecutor {
    private static final String ATTRIBUTE = RateLimitExecutor.class.getName() + ".outcomes";
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitExecutor(RateLimitGuardedInvocation guardedInvocation) {
        this.guardedInvocation = guardedInvocation;
    }

    public boolean execute(HttpServletRequest request, HttpServletResponse response, CompiledRule rule, String handler)
            throws IOException {
        if (rule == null || !rule.enabled()) return true;
        Map<String, StageGuardDecision> outcomes = outcomes(request);
        StageGuardDecision outcome;
        synchronized (outcomes) {
            outcome = outcomes.get(rule.id());
            if (outcome == null) {
                HttpRateLimitContext.bind(request);
                try {
                    outcome = guardedInvocation.evaluate(rule, handler, HttpRateLimitStageSemantics.INSTANCE);
                } finally {
                    HttpRateLimitContext.clear();
                }
                outcomes.put(rule.id(), outcome);
            }
        }
        if (outcome.allowed()) return true;
        response.setStatus(outcome.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(
                outcome.status() == rule.status() && outcome.result().equals("rejected")
                        ? rule.contentType()
                        : "application/problem+json");
        if (outcome.retryAfter() != null) {
            Duration retry = outcome.retryAfter();
            long seconds = retry.getSeconds() + (retry.getNano() == 0 ? 0 : 1);
            response.setHeader("Retry-After", Long.toString(Math.max(1, seconds)));
        }
        response.getWriter().write(outcome.body());
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, StageGuardDecision> outcomes(HttpServletRequest request) {
        synchronized (request) {
            Object value = request.getAttribute(ATTRIBUTE);
            if (value == null) {
                value = new HashMap<String, StageGuardDecision>();
                request.setAttribute(ATTRIBUTE, value);
            }
            return (Map<String, StageGuardDecision>) value;
        }
    }
}
