package io.zhijun.ratelimit.mq;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitStageSemantics;
import io.zhijun.ratelimit.web.StageGuardDecision;

enum MqRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public void enter(String selector) {
        MqRateLimitContext.enter(selector);
    }

    @Override
    public void exit() {
        MqRateLimitContext.exit();
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return new RateLimitMqRejectedException(decision.status(), decision.body(), decision.result());
    }
}
