package io.zhijun.ratelimit.druid;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitStageSemantics;
import io.zhijun.ratelimit.web.StageGuardDecision;
import java.sql.SQLException;

enum JdbcRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public void enter(String selector) {
        JdbcRateLimitContext.enter(selector);
    }

    @Override
    public void exit() {
        JdbcRateLimitContext.exit();
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return new SQLException(decision.body(), "45000", decision.status());
    }
}
