package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.ResilienceBehaviorRegistry;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.ObjectProvider;

/** Optional Resilience4j retry / time-limiter / bulkhead after rate-limit allow. */
public final class ResilienceBusinessDecorators {
    private final ObjectProvider<ResilienceBehaviorRegistry> behaviors;

    public ResilienceBusinessDecorators(ObjectProvider<ResilienceBehaviorRegistry> behaviors) {
        this.behaviors = behaviors;
    }

    public <T> T run(CompiledRule rule, Callable<T> action) throws Exception {
        Callable<T> call = action;
        ResilienceBehaviorRegistry registry = behaviors.getIfAvailable();
        if (registry != null && rule != null) {
            String ruleId = rule.id();
            if (rule.usesTimeLimiter()) {
                Callable<T> inner = call;
                call = () -> registry.executeTimeLimiter(ruleId, inner);
            }
            if (rule.usesRetry()) {
                Callable<T> inner = call;
                call = () -> registry.executeRetry(ruleId, inner);
            }
            if (rule.usesBulkhead()) {
                Callable<T> inner = call;
                call = () -> registry.executeBulkhead(ruleId, inner);
            }
        }
        return call.call();
    }
}
