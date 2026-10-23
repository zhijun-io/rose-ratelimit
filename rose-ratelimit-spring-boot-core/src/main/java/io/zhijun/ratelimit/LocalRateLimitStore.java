package io.zhijun.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.Objects;

/** Bounded admission without evicting live quota state. Restarts reset local quotas. */
public final class LocalRateLimitStore implements RateLimitStore {
    private final Cache<String, Entry> buckets;
    private final int maxBuckets;
    private final TimeMeter clock;

    public LocalRateLimitStore() {
        this(100_000, TimeMeter.SYSTEM_NANOTIME, Ticker.systemTicker());
    }

    public LocalRateLimitStore(int maxBuckets) {
        this(maxBuckets, TimeMeter.SYSTEM_NANOTIME, Ticker.systemTicker());
    }

    public LocalRateLimitStore(int maxBuckets, TimeMeter clock, Ticker ticker) {
        if (maxBuckets < 1) throw new IllegalArgumentException("maxBuckets must be positive");
        this.maxBuckets = maxBuckets;
        this.clock = Objects.requireNonNull(clock);
        buckets = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(new Expiry<String, Entry>() {
                    public long expireAfterCreate(String key, Entry value, long now) {
                        return value.retention();
                    }

                    public long expireAfterUpdate(String key, Entry value, long now, long duration) {
                        return value.retention();
                    }

                    public long expireAfterRead(String key, Entry value, long now, long duration) {
                        return value.retention();
                    }
                })
                .build();
    }

    @Override
    public String name() {
        return "local";
    }

    // Admission, lookup and consumption share one short lock; no IO occurs here.
    @Override
    public synchronized RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
        String storageKey = key + ":" + policy.fingerprint();
        Entry entry = buckets.getIfPresent(storageKey);
        if (entry == null) {
            buckets.cleanUp();
            if (buckets.estimatedSize() >= maxBuckets) {
                throw new RateLimitBackendException("Local bucket capacity exhausted");
            }
            Bucket bucket = Bucket.builder()
                    .withCustomTimePrecision(clock)
                    .addLimit(b ->
                            b.capacity(policy.capacity()).refillGreedy(policy.refillTokens(), policy.refillPeriod()))
                    .build();
            entry = new Entry(bucket, policy.retentionNanos());
            buckets.put(storageKey, entry);
        }
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        buckets.put(storageKey, entry); // Start retention after consumption, even if this thread was descheduled.
        return probe.isConsumed()
                ? RateLimitDecision.allowed(probe.getRemainingTokens())
                : RateLimitDecision.rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private record Entry(Bucket bucket, long retention) {}
}
