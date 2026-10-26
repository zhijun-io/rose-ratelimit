package io.zhijun.ratelimit.resilience4j;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.zhijun.ratelimit.RetrySpec;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

final class Resilience4jRetryRegistry {
    private final Map<String, Retry> retries;

    public Resilience4jRetryRegistry(RateLimitRules rules) {
        Map<String, Retry> built = new HashMap<>();
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || !rule.usesRetry() || rule.retry() == null) {
                continue;
            }
            String name = rules.namespace() + ":" + rule.id() + ":retry";
            built.put(rule.id(), Retry.of(name, toConfig(rule.retry())));
        }
        retries = Map.copyOf(built);
    }

    <T> T execute(String ruleId, Callable<T> action) throws Exception {
        Retry retry = retries.get(ruleId);
        if (retry == null) {
            return action.call();
        }
        return retry.executeCallable(action);
    }

    private static RetryConfig toConfig(RetrySpec spec) {
        return RetryConfig.custom()
                .maxAttempts(spec.maxAttempts())
                .waitDuration(spec.waitDuration())
                .failAfterMaxAttempts(true)
                .build();
    }
}
