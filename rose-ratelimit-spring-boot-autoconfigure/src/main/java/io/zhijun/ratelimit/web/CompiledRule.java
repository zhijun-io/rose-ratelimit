package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import java.util.Set;
import org.springframework.web.util.pattern.PathPattern;

/** Validated startup snapshot; no mutable configuration objects cross into request processing. */
public record CompiledRule(
        String id,
        boolean enabled,
        Stage stage,
        PathPattern path,
        Set<String> methods,
        String handler,
        Scope scope,
        RateLimitPolicy policy,
        RateLimitStore store,
        RateLimitKeyResolver resolver,
        String keyHeader,
        MissingKey missingKey,
        FailureMode failureMode,
        int status,
        String contentType,
        String body) {}
