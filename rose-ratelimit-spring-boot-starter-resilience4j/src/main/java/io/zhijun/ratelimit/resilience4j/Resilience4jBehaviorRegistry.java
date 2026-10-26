package io.zhijun.ratelimit.resilience4j;

import io.zhijun.ratelimit.ResilienceBehaviorRegistry;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.concurrent.Callable;

public final class Resilience4jBehaviorRegistry implements ResilienceBehaviorRegistry {
    private final Resilience4jTimeLimiterRegistry timeLimiters;
    private final Resilience4jRetryRegistry retries;
    private final Resilience4jBulkheadRegistry bulkheads;

    public Resilience4jBehaviorRegistry(RateLimitRules rules) {
        this.timeLimiters = new Resilience4jTimeLimiterRegistry(rules);
        this.retries = new Resilience4jRetryRegistry(rules);
        this.bulkheads = new Resilience4jBulkheadRegistry(rules);
    }

    @Override
    public <T> T executeTimeLimiter(String ruleId, Callable<T> action) throws Exception {
        return timeLimiters.execute(ruleId, action);
    }

    @Override
    public <T> T executeRetry(String ruleId, Callable<T> action) throws Exception {
        return retries.execute(ruleId, action);
    }

    @Override
    public <T> T executeBulkhead(String ruleId, Callable<T> action) throws Exception {
        return bulkheads.execute(ruleId, action);
    }
}
