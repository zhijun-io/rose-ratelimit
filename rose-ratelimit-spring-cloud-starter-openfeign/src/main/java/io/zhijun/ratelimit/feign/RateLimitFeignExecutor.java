package io.zhijun.ratelimit.feign;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import java.util.concurrent.Callable;

/** Feign stage: rate-limit / circuit decisions before the delegate invocation. */
public final class RateLimitFeignExecutor {
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitFeignExecutor(RateLimitGuardedInvocation guardedInvocation) {
        this.guardedInvocation = guardedInvocation;
    }

    public <T> T invokeGuarded(CompiledRule rule, String selector, Callable<T> call) throws Exception {
        return guardedInvocation.invokeGuarded(rule, selector, call, FeignRateLimitStageSemantics.INSTANCE);
    }
}
