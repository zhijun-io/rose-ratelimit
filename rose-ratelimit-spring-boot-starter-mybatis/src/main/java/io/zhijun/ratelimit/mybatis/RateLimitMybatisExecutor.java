package io.zhijun.ratelimit.mybatis;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import java.util.concurrent.Callable;

/** MyBatis stage: rate-limit / circuit decisions before statement execution. */
public final class RateLimitMybatisExecutor {
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitMybatisExecutor(RateLimitGuardedInvocation guardedInvocation) {
        this.guardedInvocation = guardedInvocation;
    }

    public <T> T invokeGuarded(CompiledRule rule, String statementId, Callable<T> call) throws Exception {
        return guardedInvocation.invokeGuarded(rule, statementId, call, MybatisRateLimitStageSemantics.INSTANCE);
    }
}
