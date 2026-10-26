package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.InvokeRateLimitContext;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;

final class InvokeRateLimitKeyResolver implements RateLimitKeyResolver {
    @Override
    public String name() {
        return "invoke";
    }

    @Override
    public String resolve(HttpServletRequest request, String headerName) {
        String selector = InvokeRateLimitContext.currentSelector();
        return selector == null || selector.isBlank() ? null : selector;
    }
}
