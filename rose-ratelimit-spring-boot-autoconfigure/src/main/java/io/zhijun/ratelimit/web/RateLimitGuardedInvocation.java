package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.RateLimitOptions.Scope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/** Shared rate-limit / circuit decision and optional business decoration for all stages. */
public final class RateLimitGuardedInvocation {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitGuardedInvocation.class);
    private final RateLimitRules rules;
    private final List<RateLimitObserver> observers;
    private final ObjectProvider<ResiliencePipelineRegistry> pipelines;
    private final ResilienceBusinessDecorators businessDecorators;
    private final RoseResilienceInvocation invocation = new RoseResilienceInvocation();
    private final Map<String, AtomicLong> lastWarnings = new ConcurrentHashMap<>();

    public RateLimitGuardedInvocation(
            RateLimitRules rules,
            List<RateLimitObserver> observers,
            ObjectProvider<ResiliencePipelineRegistry> pipelines,
            ResilienceBusinessDecorators businessDecorators) {
        this.rules = rules;
        this.observers = List.copyOf(observers);
        this.pipelines = pipelines;
        this.businessDecorators = businessDecorators;
    }

    /** HTTP Filter/MVC: decision only (response mapping stays in {@link RateLimitExecutor}). */
    public StageGuardDecision evaluate(CompiledRule rule, String selector, RateLimitStageSemantics stage) {
        if (rule == null || !rule.enabled()) {
            return new StageGuardDecision(0, "", "allowed");
        }
        stage.enter(selector);
        try {
            return decideAndObserve(rule, selector, stage);
        } finally {
            stage.exit();
        }
    }

    public <T> T invokeGuarded(CompiledRule rule, String selector, Callable<T> call, RateLimitStageSemantics stage)
            throws Exception {
        if (rule == null || !rule.enabled()) {
            return call.call();
        }
        stage.enter(selector);
        try {
            StageGuardDecision outcome = decideAndObserve(rule, selector, stage);
            if (!outcome.allowed()) {
                throw stage.reject(outcome, rule);
            }
            return businessDecorators.run(rule, call);
        } finally {
            stage.exit();
        }
    }

    private StageGuardDecision decideAndObserve(CompiledRule rule, String selector, RateLimitStageSemantics stage) {
        StageGuardDecision outcome = decide(rule, selector, stage);
        observe(rule, outcome.result());
        return outcome;
    }

    private StageGuardDecision decide(CompiledRule rule, String selector, RateLimitStageSemantics stage) {
        String identity = stage.resolveIdentity(rule, selector);
        boolean missing = identity == null || identity.isBlank();
        String scope = rule.scope() == Scope.HANDLER ? Objects.requireNonNull(selector, "selector") : "rule";
        String storageKey = RateLimitKeys.of(
                rules.namespace(), rule.id(), scope, missing ? "missing" : "present", missing ? "" : identity);
        RoseResilienceRequest resilienceRequest = new RoseResilienceRequest(
                storageKey,
                rule.policy(),
                rule.store(),
                stage.missingKey(rule.missingKey()),
                identity,
                stage.failureMode(rule.failureMode()));
        ResiliencePipelineRegistry registry = pipelines.getIfAvailable();
        RoseResilienceOutcome coreOutcome = registry == null
                ? invocation.invoke(resilienceRequest)
                : registry.pipelineFor(rule.id())
                        .map(pipeline -> pipeline.invoke(resilienceRequest))
                        .orElseGet(() -> invocation.invoke(resilienceRequest));
        if (coreOutcome.result() == RoseResilienceOutcome.Result.BACKEND_OPEN
                || coreOutcome.result() == RoseResilienceOutcome.Result.BACKEND_CLOSED) {
            warnBackendOnce(rule);
        }
        return stage.map(coreOutcome, rule);
    }

    private void warnBackendOnce(CompiledRule rule) {
        AtomicLong gate = lastWarnings.computeIfAbsent(rule.id(), ignored -> new AtomicLong());
        long now = System.nanoTime();
        long previous = gate.get();
        if ((previous == 0 || now - previous >= Duration.ofMinutes(1).toNanos()) && gate.compareAndSet(previous, now)) {
            LOG.warn(
                    "Rate-limit backend failure: rule={}, backend={}, mode={}",
                    rule.id(),
                    rule.store().name(),
                    rule.failureMode());
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
}
