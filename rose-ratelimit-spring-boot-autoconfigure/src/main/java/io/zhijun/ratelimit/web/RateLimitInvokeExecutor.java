package io.zhijun.ratelimit.web;

import java.util.concurrent.Callable;

/** INVOKE stage: rate-limit / circuit decisions around explicit calls. */
public final class RateLimitInvokeExecutor {
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitInvokeExecutor(RateLimitGuardedInvocation guardedInvocation) {
        this.guardedInvocation = guardedInvocation;
    }

    public <T> T invokeGuarded(CompiledRule rule, String selector, Callable<T> call) throws Exception {
        return guardedInvocation.invokeGuarded(rule, selector, call, InvokeRateLimitStageSemantics.INSTANCE);
    }
}
