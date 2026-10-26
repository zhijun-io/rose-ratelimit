package io.zhijun.ratelimit.web;

import jakarta.servlet.http.HttpServletRequest;

/** Thread-local HTTP request for {@link HttpRateLimitStageSemantics} during {@link RateLimitExecutor#execute}. */
final class HttpRateLimitContext {
    private static final ThreadLocal<HttpServletRequest> REQUEST = new ThreadLocal<>();

    private HttpRateLimitContext() {}

    static void bind(HttpServletRequest request) {
        REQUEST.set(request);
    }

    static void clear() {
        REQUEST.remove();
    }

    static HttpServletRequest request() {
        HttpServletRequest request = REQUEST.get();
        if (request == null) {
            throw new IllegalStateException("No HTTP request bound for rate-limit decision");
        }
        return request;
    }
}
