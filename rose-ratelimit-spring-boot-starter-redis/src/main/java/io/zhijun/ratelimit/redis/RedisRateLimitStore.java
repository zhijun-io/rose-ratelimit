package io.zhijun.ratelimit.redis;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.zhijun.ratelimit.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Bucket4j CAS adapter. Owns its connection, never the Boot-managed Redis client. */
public final class RedisRateLimitStore implements RateLimitStore, AutoCloseable {
    private final Supplier<AbstractRedisClient> client;
    private final Duration timeout;
    private final int maxRetries;
    private volatile ProxyManager<String> manager;
    private AutoCloseable connection;
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
        ProxyManager<String> activeManager = manager();
        try {
            ConsumptionProbe probe = activeManager
                    .builder()
                    .build(storageKey(key, policy), bucketConfiguration(policy))
                    .tryConsumeAndReturnRemaining(1);
            return probe.isConsumed()
                    ? RateLimitDecision.allowed(probe.getRemainingTokens())
                    : RateLimitDecision.rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException e) {
            if (!isBackendFailure(e)) throw e;
            discardConnection(activeManager);
            throw new RateLimitBackendException("Redis rate-limit operation failed", e);
        }
    }

    private static String storageKey(String key, RateLimitPolicy policy) {
        return "rose:ratelimit:v2:" + key + ":" + policy.fingerprint();
    }

    private static BucketConfiguration bucketConfiguration(RateLimitPolicy policy) {
        return BucketConfiguration.builder()
                .addLimit(limit ->
                        limit.capacity(policy.capacity()).refillGreedy(policy.refillTokens(), policy.refillPeriod()))
                .build();
    }

    private static boolean isBackendFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof IllegalStateException illegalState) {
                if ("Rate-limit store is closed".equals(illegalState.getMessage())) return false;
                return true;
            }
            if (current instanceof RedisException || current instanceof IOException) return true;
            if (current instanceof RateLimitBackendException) return true;
            String name = current.getClass().getName();
            if (name.startsWith("io.github.bucket4j.")) return true;
        }
        return false;
    }

    private synchronized void discardConnection(ProxyManager<String> failedManager) {
        if (manager != failedManager) return;
        manager = null;
        closeConnection();
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // best-effort close
            }
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
                return openStandaloneManager(standalone, codec, expiration);
            }
            if (nativeClient instanceof RedisClusterClient cluster) {
                return openClusterManager(cluster, codec, expiration);
            }
            throw new IllegalStateException("Redis limiter requires a Lettuce RedisClient or RedisClusterClient");
        }
    }

    private ProxyManager<String> openStandaloneManager(
            RedisClient standalone, RedisCodec<String, byte[]> codec, ExpirationAfterWriteStrategy expiration) {
        StatefulRedisConnection<String, byte[]> opened = null;
        try {
            opened = standalone.connect(codec);
            opened.setTimeout(timeout);
            manager = Bucket4jLettuce.casBasedBuilder(opened)
                    .requestTimeout(timeout)
                    .maxRetries(maxRetries)
                    .expirationAfterWrite(expiration)
                    .build();
            connection = opened;
            return manager;
        } catch (RuntimeException e) {
            if (opened != null) opened.close();
            return rethrowConnectionFailure(e);
        }
    }

    private ProxyManager<String> openClusterManager(
            RedisClusterClient cluster, RedisCodec<String, byte[]> codec, ExpirationAfterWriteStrategy expiration) {
        StatefulRedisClusterConnection<String, byte[]> opened = null;
        try {
            opened = cluster.connect(codec);
            opened.setTimeout(timeout);
            manager = Bucket4jLettuce.casBasedBuilder(opened)
                    .requestTimeout(timeout)
                    .maxRetries(maxRetries)
                    .expirationAfterWrite(expiration)
                    .build();
            connection = opened;
            return manager;
        } catch (RuntimeException e) {
            if (opened != null) opened.close();
            return rethrowConnectionFailure(e);
        }
    }

    private ProxyManager<String> rethrowConnectionFailure(RuntimeException error) {
        if (isBackendFailure(error)) {
            throw new RateLimitBackendException("Redis rate-limit connection failed", error);
        }
        throw error;
    }

    @Override
    public synchronized void close() {
        closed = true;
        manager = null;
        closeConnection();
    }
}
