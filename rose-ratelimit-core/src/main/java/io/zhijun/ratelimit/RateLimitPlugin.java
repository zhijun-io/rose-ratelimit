package io.zhijun.ratelimit;

/** A named capability discovered as a Spring bean by the integration layer. */
public interface RateLimitPlugin {
    String name();
}
