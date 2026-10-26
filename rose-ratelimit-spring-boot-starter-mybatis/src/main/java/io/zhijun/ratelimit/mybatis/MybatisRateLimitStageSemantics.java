package io.zhijun.ratelimit.mybatis;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitStageSemantics;
import io.zhijun.ratelimit.web.StageGuardDecision;
import java.sql.SQLException;

enum MybatisRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public void enter(String selector) {
        MybatisRateLimitContext.enter(selector);
    }

    @Override
    public void exit() {
        MybatisRateLimitContext.exit();
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return new SQLException(decision.body(), "45000", decision.status());
    }
}
