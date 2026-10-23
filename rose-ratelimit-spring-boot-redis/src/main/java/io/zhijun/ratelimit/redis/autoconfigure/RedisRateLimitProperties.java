package io.zhijun.ratelimit.redis.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("rose.ratelimit.redis")
public record RedisRateLimitProperties(
        @DefaultValue("1s") Duration timeout,
        @DefaultValue("8") int maxRetries) {
    public RedisRateLimitProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || maxRetries < 1) {
            throw new IllegalArgumentException("rose.ratelimit.redis.timeout and max-retries must be positive");
        }
        timeout.toNanos();
    }
}
