package io.zhijun.ratelimit.resilience4j;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.zhijun.ratelimit.TimeLimiterSpec;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class Resilience4jTimeLimiterRegistry {
    private final Map<String, TimeLimiter> limiters;

    public Resilience4jTimeLimiterRegistry(RateLimitRules rules) {
        Map<String, TimeLimiter> built = new HashMap<>();
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || !rule.usesTimeLimiter() || rule.timeLimiter() == null) {
                continue;
            }
            String name = rules.namespace() + ":" + rule.id() + ":time-limiter";
            built.put(rule.id(), TimeLimiter.of(name, toConfig(rule.timeLimiter())));
        }
        limiters = Map.copyOf(built);
    }

    <T> T execute(String ruleId, Callable<T> action) throws Exception {
        TimeLimiter limiter = limiters.get(ruleId);
        if (limiter == null) {
            return action.call();
        }
        try {
            return limiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(() -> {
                try {
                    return action.call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }));
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    private static TimeLimiterConfig toConfig(TimeLimiterSpec spec) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(spec.timeoutDuration())
                .cancelRunningFuture(spec.cancelRunningFuture())
                .build();
    }
}
