package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared request execution, including async redispatch deduplication and typed backend failure handling. */
public final class RateLimitExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitExecutor.class);
    private static final String ATTRIBUTE = RateLimitExecutor.class.getName() + ".outcomes";
    private final RateLimitRules rules;
    private final List<RateLimitObserver> observers;
    private final Map<String, AtomicLong> lastWarnings = new ConcurrentHashMap<>();

    public RateLimitExecutor(RateLimitRules rules, List<RateLimitObserver> observers) {
        this.rules = rules;
        this.observers = List.copyOf(observers);
    }

    public boolean execute(HttpServletRequest request, HttpServletResponse response, CompiledRule rule, String handler)
            throws IOException {
        if (rule == null || !rule.enabled()) return true;
        Map<String, Outcome> outcomes = outcomes(request);
        Outcome outcome;
        synchronized (outcomes) {
            outcome = outcomes.get(rule.id());
            if (outcome == null) {
                outcome = decide(request, rule, handler);
                outcomes.put(rule.id(), outcome);
                observe(rule, outcome.result());
            }
        }
        if (outcome.status() == 0) return true;
        response.setStatus(outcome.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(
                outcome.status() == rule.status() && outcome.result().equals("rejected")
                        ? rule.contentType()
                        : "application/problem+json");
        if (outcome.retry() != null) {
            Duration retry = outcome.retry();
            long seconds = retry.getSeconds() + (retry.getNano() == 0 ? 0 : 1);
            response.setHeader("Retry-After", Long.toString(Math.max(1, seconds)));
        }
        response.getWriter().write(outcome.body());
        return false;
    }

    private Outcome decide(HttpServletRequest request, CompiledRule rule, String handler) {
        // Resolver/programming exceptions must not be converted into fail-open.
        String identity = rule.resolver().resolve(request, rule.keyHeader());
        boolean missing = identity == null || identity.isBlank();
        if (missing && rule.missingKey() == MissingKey.REJECT) {
            return new Outcome(
                    400, "{\"title\":\"Rate limit identity is missing\",\"status\":400}", null, "missing_key");
        }
        String scope = rule.scope() == Scope.HANDLER ? Objects.requireNonNull(handler, "handler") : "rule";
        String key = RateLimitKeys.of(
                rules.namespace(), rule.id(), scope, missing ? "missing" : "present", missing ? "" : identity);
        try {
            RateLimitDecision decision = rule.store().tryAcquire(key, rule.policy());
            return decision.allowed()
                    ? new Outcome(0, "", null, "allowed")
                    : new Outcome(rule.status(), rule.body(), decision.retryAfter(), "rejected");
        } catch (RateLimitBackendException e) {
            AtomicLong gate = lastWarnings.computeIfAbsent(rule.id(), ignored -> new AtomicLong());
            long now = System.nanoTime();
            long previous = gate.get();
            if ((previous == 0 || now - previous >= Duration.ofMinutes(1).toNanos())
                    && gate.compareAndSet(previous, now)) {
                LOG.warn(
                        "Rate-limit backend failure: rule={}, backend={}, mode={}, error={}",
                        rule.id(),
                        rule.store().name(),
                        rule.failureMode(),
                        e.getClass().getSimpleName());
            }
            return rule.failureMode() == FailureMode.OPEN
                    ? new Outcome(0, "", null, "backend_error_open")
                    : new Outcome(
                            503,
                            "{\"title\":\"Rate limit service unavailable\",\"status\":503}",
                            null,
                            "backend_error_closed");
        }
    }

    private void observe(CompiledRule rule, String result) {
        for (RateLimitObserver observer : observers) {
            try {
                observer.onDecision(rule.id(), rule.store().name(), result);
            } catch (RuntimeException ignored) {
                // Observability must not change an already committed quota decision.
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Outcome> outcomes(HttpServletRequest request) {
        synchronized (request) {
            Object value = request.getAttribute(ATTRIBUTE);
            if (value == null) {
                value = new HashMap<String, Outcome>();
                request.setAttribute(ATTRIBUTE, value);
            }
            return (Map<String, Outcome>) value;
        }
    }

    private record Outcome(int status, String body, Duration retry, String result) {}
}
