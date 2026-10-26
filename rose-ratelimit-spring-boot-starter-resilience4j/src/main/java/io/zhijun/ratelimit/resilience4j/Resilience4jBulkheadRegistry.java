package io.zhijun.ratelimit.resilience4j;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.zhijun.ratelimit.BulkheadSpec;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

final class Resilience4jBulkheadRegistry {
    private final Map<String, Bulkhead> bulkheads;

    public Resilience4jBulkheadRegistry(RateLimitRules rules) {
        Map<String, Bulkhead> built = new HashMap<>();
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || !rule.usesBulkhead() || rule.bulkhead() == null) {
                continue;
            }
            String name = rules.namespace() + ":" + rule.id() + ":bulkhead";
            built.put(rule.id(), Bulkhead.of(name, toConfig(rule.bulkhead())));
        }
        bulkheads = Map.copyOf(built);
    }

    <T> T execute(String ruleId, Callable<T> action) throws Exception {
        Bulkhead bulkhead = bulkheads.get(ruleId);
        if (bulkhead == null) {
            return action.call();
        }
        return Bulkhead.decorateCallable(bulkhead, action).call();
    }

    private static BulkheadConfig toConfig(BulkheadSpec spec) {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(spec.maxConcurrentCalls())
                .maxWaitDuration(spec.maxWaitDuration())
                .build();
    }
}
