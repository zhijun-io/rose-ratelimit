package io.zhijun.ratelimit;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Objects;

/** Immutable greedy token bucket; one request consumes one token. */
public record RateLimitPolicy(long capacity, long refillTokens, Duration refillPeriod) {
    public RateLimitPolicy {
        Objects.requireNonNull(refillPeriod, "refillPeriod");
        if (capacity < 1 || refillTokens < 1 || refillPeriod.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("capacity/refillTokens must be positive; refillPeriod must be >= 1ms");
        }
        retentionNanos(capacity, refillTokens, refillPeriod);
    }

    public long retentionNanos() {
        return retentionNanos(capacity, refillTokens, refillPeriod);
    }

    private static long retentionNanos(long capacity, long refill, Duration period) {
        BigInteger numerator = BigInteger.valueOf(capacity).multiply(BigInteger.valueOf(period.toNanos()));
        BigInteger divisor = BigInteger.valueOf(refill);
        return numerator
                .add(divisor)
                .subtract(BigInteger.ONE)
                .divide(divisor)
                .add(BigInteger.valueOf(Duration.ofSeconds(1).toNanos()))
                .longValueExact();
    }

    public String fingerprint() {
        return capacity + ":" + refillTokens + ":" + refillPeriod.toNanos();
    }
}
