package io.zhijun.ratelimit.web;

enum InvokeRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public void enter(String selector) {
        InvokeRateLimitContext.enter(selector);
    }

    @Override
    public void exit() {
        InvokeRateLimitContext.exit();
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return new RateLimitInvokeRejectedException(decision.status(), decision.body(), decision.result());
    }
}
