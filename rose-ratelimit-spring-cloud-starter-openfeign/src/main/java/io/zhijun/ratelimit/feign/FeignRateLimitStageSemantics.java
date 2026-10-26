package io.zhijun.ratelimit.feign;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitStageSemantics;
import io.zhijun.ratelimit.web.StageGuardDecision;

enum FeignRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public void enter(String selector) {
        FeignRateLimitContext.enter(selector);
    }

    @Override
    public void exit() {
        FeignRateLimitContext.exit();
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return FeignRateLimitStageAdapter.toException(decision);
    }
}
