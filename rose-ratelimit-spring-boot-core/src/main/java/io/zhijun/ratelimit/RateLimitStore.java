package io.zhijun.ratelimit;

/** Atomically decides and consumes one token. Backend failures must use the typed exception. */
public interface RateLimitStore extends RateLimitPlugin {
    RateLimitDecision tryAcquire(String key, RateLimitPolicy policy);
}
