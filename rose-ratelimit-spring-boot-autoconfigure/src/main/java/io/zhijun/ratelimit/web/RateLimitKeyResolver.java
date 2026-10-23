package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.RateLimitPlugin;
import jakarta.servlet.http.HttpServletRequest;

/** Return null for missing identity. Resolvers must not read request bodies or log credentials. */
public interface RateLimitKeyResolver extends RateLimitPlugin {
    String resolve(HttpServletRequest request, String headerName);

    default void validate(String headerName) {}
}
