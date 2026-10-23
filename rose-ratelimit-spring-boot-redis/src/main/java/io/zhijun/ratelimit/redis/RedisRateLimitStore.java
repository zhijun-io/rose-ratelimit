package io.zhijun.ratelimit.redis;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.zhijun.ratelimit.*;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Bucket4j CAS adapter. Owns its connection, never the Boot-managed Redis client. */
public final class RedisRateLimitStore implements RateLimitStore, AutoCloseable {
    private final Supplier<AbstractRedisClient> client;
    private final Duration timeout;
    private final int maxRetries;
    private volatile ProxyManager<String> manager;
    private StatefulConnection<String, byte[]> connection;
    private volatile boolean closed;

    public RedisRateLimitStore(Supplier<AbstractRedisClient> client, Duration timeout, int maxRetries) {
        this.client = Objects.requireNonNull(client);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || maxRetries < 1) {
            throw new IllegalArgumentException("Redis timeout and max-retries must be positive");
        }
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
        var configuration = policy.configuration();
        ProxyManager<String> activeManager = null;
        try {
            activeManager = manager();
            ConsumptionProbe probe = activeManager
                    .builder()
                    .build("rose:ratelimit:v2:" + key + ":" + policy.fingerprint(), configuration)
                    .tryConsumeAndReturnRemaining(1);
            return probe.isConsumed()
                    ? RateLimitDecision.allowed(probe.getRemainingTokens())
                    : RateLimitDecision.rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException e) {
            if (activeManager != null) discardConnection(activeManager);
            throw new RateLimitBackendException("Redis rate-limit operation failed", e);
        }
    }

    private synchronized void discardConnection(ProxyManager<String> failedManager) {
        if (manager != failedManager) return;
        manager = null;
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private ProxyManager<String> manager() {
        if (closed) throw new IllegalStateException("Rate-limit store is closed");
        ProxyManager<String> current = manager;
        if (current != null) return current;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Rate-limit store is closed");
            if (manager != null) return manager;
            var codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
            var expiration = ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(1));
            AbstractRedisClient nativeClient = client.get();
            if (nativeClient instanceof RedisClient standalone) {
                var opened = standalone.connect(codec);
                connection = opened;
                opened.setTimeout(timeout);
                manager = Bucket4jLettuce.casBasedBuilder(opened)
                        .requestTimeout(timeout)
                        .maxRetries(maxRetries)
                        .expirationAfterWrite(expiration)
                        .build();
            } else if (nativeClient instanceof RedisClusterClient cluster) {
                var opened = cluster.connect(codec);
                connection = opened;
                opened.setTimeout(timeout);
                manager = Bucket4jLettuce.casBasedBuilder(opened)
                        .requestTimeout(timeout)
                        .maxRetries(maxRetries)
                        .expirationAfterWrite(expiration)
                        .build();
            } else {
                throw new IllegalStateException("Redis limiter requires a Lettuce RedisClient or RedisClusterClient");
            }
            return manager;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (connection != null) connection.close();
    }
}
