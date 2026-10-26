package io.zhijun.ratelimit.mybatis;

import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;

/** Uses the current mapped statement id as the rate-limit identity. */
public final class MybatisRateLimitKeyResolver implements RateLimitKeyResolver {
    @Override
    public String name() {
        return "mybatis";
    }

    @Override
    public String resolve(HttpServletRequest request, String headerName) {
        String statementId = MybatisRateLimitContext.currentStatementId();
        return statementId == null || statementId.isBlank() ? null : statementId;
    }
}
