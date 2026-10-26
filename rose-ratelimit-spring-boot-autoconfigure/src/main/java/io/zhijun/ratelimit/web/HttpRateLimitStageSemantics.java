package io.zhijun.ratelimit.web;

enum HttpRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public String resolveIdentity(CompiledRule rule, String selector) {
        return rule.resolver().resolve(HttpRateLimitContext.request(), rule.keyHeader());
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return new IllegalStateException("HTTP rejections are written to the servlet response");
    }
}
