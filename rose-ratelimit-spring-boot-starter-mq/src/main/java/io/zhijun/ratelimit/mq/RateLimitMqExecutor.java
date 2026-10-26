package io.zhijun.ratelimit.mq;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import java.util.concurrent.Callable;

/** MQ stage: decisions before listener invocation. */
public final class RateLimitMqExecutor {
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitMqExecutor(RateLimitGuardedInvocation guardedInvocation) {
        this.guardedInvocation = guardedInvocation;
    }

    public <T> T invokeGuarded(CompiledRule rule, String selector, Callable<T> call) throws Exception {
        return guardedInvocation.invokeGuarded(rule, selector, call, MqRateLimitStageSemantics.INSTANCE);
    }
}
