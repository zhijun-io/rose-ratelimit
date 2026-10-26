package io.zhijun.ratelimit.mq;

import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;

/** Uses the current MQ listener selector as the rate-limit identity. */
public final class MqRateLimitKeyResolver implements RateLimitKeyResolver {
    @Override
    public String name() {
        return "mq";
    }

    @Override
    public String resolve(HttpServletRequest request, String headerName) {
        String selector = MqRateLimitContext.currentSelector();
        return selector == null || selector.isBlank() ? null : selector;
    }
}
