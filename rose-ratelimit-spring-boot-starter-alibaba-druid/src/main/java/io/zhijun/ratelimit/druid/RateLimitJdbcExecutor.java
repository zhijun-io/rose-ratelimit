package io.zhijun.ratelimit.druid;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import java.util.concurrent.Callable;

/** JDBC stage: rate-limit / circuit decisions around SQL execution. */
public final class RateLimitJdbcExecutor {
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitJdbcExecutor(RateLimitGuardedInvocation guardedInvocation) {
        this.guardedInvocation = guardedInvocation;
    }

    public <T> T invokeGuarded(CompiledRule rule, String selector, Callable<T> call) throws Exception {
        return guardedInvocation.invokeGuarded(rule, selector, call, JdbcRateLimitStageSemantics.INSTANCE);
    }
}
