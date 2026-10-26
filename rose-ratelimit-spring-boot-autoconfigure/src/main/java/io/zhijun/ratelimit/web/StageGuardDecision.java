package io.zhijun.ratelimit.web;

import java.time.Duration;

/** Normalized stage guard outcome for {@link RateLimitGuardedInvocation}. */
public record StageGuardDecision(int status, String body, String result, Duration retryAfter) {
    public StageGuardDecision(int status, String body, String result) {
        this(status, body, result, null);
    }

    public boolean allowed() {
        return status == 0;
    }
}
