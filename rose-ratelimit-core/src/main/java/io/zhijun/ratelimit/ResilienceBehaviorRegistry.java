package io.zhijun.ratelimit;

import java.util.concurrent.Callable;

/** Optional Resilience4j retry / time-limiter / bulkhead for guarded business calls. */
public interface ResilienceBehaviorRegistry {
    <T> T executeTimeLimiter(String ruleId, Callable<T> action) throws Exception;

    <T> T executeRetry(String ruleId, Callable<T> action) throws Exception;

    <T> T executeBulkhead(String ruleId, Callable<T> action) throws Exception;
}
