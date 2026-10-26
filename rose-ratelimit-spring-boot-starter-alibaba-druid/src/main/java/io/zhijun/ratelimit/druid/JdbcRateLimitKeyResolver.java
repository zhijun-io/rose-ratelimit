package io.zhijun.ratelimit.druid;

import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;

/** Uses the current JDBC selector as the rate-limit identity. */
public final class JdbcRateLimitKeyResolver implements RateLimitKeyResolver {
    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public String resolve(HttpServletRequest request, String headerName) {
        String selector = JdbcRateLimitContext.currentSelector();
        return selector == null || selector.isBlank() ? null : selector;
    }
}
