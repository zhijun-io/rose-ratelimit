package io.zhijun.ratelimit;

import io.github.bucket4j.BucketConfiguration;
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
        // Both backends use the same library validation and bounded retention calculation.
        BucketConfiguration.builder()
                .addLimit(b -> b.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .build();
        retentionNanos(capacity, refillTokens, refillPeriod);
    }

    public BucketConfiguration configuration() {
        return BucketConfiguration.builder()
                .addLimit(b -> b.capacity(capacity).refillGreedy(refillTokens, refillPeriod))
                .build();
    }

    public long retentionNanos() {
        return retentionNanos(capacity, refillTokens, refillPeriod);
    }

    private static long retentionNanos(long capacity, long refill, Duration period) {
        BigInteger numerator = BigInteger.valueOf(capacity).multiply(BigInteger.valueOf(period.toNanos()));
        BigInteger divisor = BigInteger.valueOf(refill);
        // Full refill from empty, rounded up, with a clock/expiry margin.
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
