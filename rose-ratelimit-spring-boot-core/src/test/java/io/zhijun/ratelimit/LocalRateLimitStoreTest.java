package io.zhijun.ratelimit;

import static org.assertj.core.api.Assertions.*;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LocalRateLimitStoreTest {
    private static final class Clock implements TimeMeter, Ticker {
        private final AtomicLong now = new AtomicLong();

        public long currentTimeNanos() {
            return now.get();
        }

        public boolean isWallClockBased() {
            return false;
        }

        public long read() {
            return now.get();
        }

        void advance(Duration duration) {
            now.addAndGet(duration.toNanos());
        }
    }

    @Test
    void dailyQuotaDoesNotResetAfterAnIdleHour() {
        Clock clock = new Clock();
        var store = new LocalRateLimitStore(10, clock, clock);
        var policy = new RateLimitPolicy(1, 1, Duration.ofDays(1));
        assertThat(store.tryAcquire("key", policy).allowed()).isTrue();
        clock.advance(Duration.ofHours(2));
        assertThat(store.tryAcquire("key", policy).allowed()).isFalse();
        clock.advance(Duration.ofHours(22));
        assertThat(store.tryAcquire("key", policy).allowed()).isTrue();
    }

    @Test
    void saturationDoesNotEvictAnActiveBucketAndExpiredBucketsFreeCapacity() {
        Clock clock = new Clock();
        var store = new LocalRateLimitStore(1, clock, clock);
        var policy = new RateLimitPolicy(1, 1, Duration.ofDays(1));
        assertThat(store.tryAcquire("first", policy).allowed()).isTrue();
        assertThatThrownBy(() -> store.tryAcquire("new", policy)).isInstanceOf(RateLimitBackendException.class);
        assertThat(store.tryAcquire("first", policy).allowed()).isFalse();
        clock.advance(Duration.ofDays(1).plusSeconds(2));
        assertThat(store.tryAcquire("new", policy).allowed()).isTrue();
    }

    @Test
    void concurrentRequestsConsumeExactlyTheConfiguredCapacity() throws Exception {
        Clock clock = new Clock();
        var store = new LocalRateLimitStore(10, clock, clock);
        var policy = new RateLimitPolicy(10, 1, Duration.ofDays(1));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 200; i++)
                futures.add(
                        executor.submit(() -> store.tryAcquire("same", policy).allowed()));
            int allowed = 0;
            for (var future : futures) if (future.get()) allowed++;
            assertThat(allowed).isEqualTo(10);
        }
    }

    @Test
    void refillAndRetryUseTheSamePolicy() {
        Clock clock = new Clock();
        var store = new LocalRateLimitStore(1, clock, clock);
        var policy = new RateLimitPolicy(1, 1, Duration.ofSeconds(2));
        store.tryAcquire("key", policy);
        clock.advance(Duration.ofMillis(100));
        assertThat(store.tryAcquire("key", policy).retryAfter()).isEqualTo(Duration.ofMillis(1900));
        clock.advance(Duration.ofMillis(1900));
        assertThat(store.tryAcquire("key", policy).allowed()).isTrue();
    }

    @Test
    void invalidPeriodsAreRejectedBeforeStorage() {
        assertThatThrownBy(() -> new RateLimitPolicy(1, 1, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(1, 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysSeparateNamespaceAndUnambiguousIdentityComponents() {
        assertThat(RateLimitKeys.of("app1", "rule", "client")).isNotEqualTo(RateLimitKeys.of("app2", "rule", "client"));
        assertThat(RateLimitKeys.of("a", "bc")).isNotEqualTo(RateLimitKeys.of("ab", "c"));
        assertThat(RateLimitKeys.of("rule", "missing", ""))
                .isNotEqualTo(RateLimitKeys.of("rule", "present", "missing"));
    }
}
