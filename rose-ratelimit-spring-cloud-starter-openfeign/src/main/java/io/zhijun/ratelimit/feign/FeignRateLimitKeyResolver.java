package io.zhijun.ratelimit.feign;

import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;

/** Uses the current Feign method selector as the rate-limit identity. */
public final class FeignRateLimitKeyResolver implements RateLimitKeyResolver {
    @Override
    public String name() {
        return "feign";
    }

    @Override
    public String resolve(HttpServletRequest request, String headerName) {
        String selector = FeignRateLimitContext.currentSelector();
        return selector == null || selector.isBlank() ? null : selector;
    }
}
