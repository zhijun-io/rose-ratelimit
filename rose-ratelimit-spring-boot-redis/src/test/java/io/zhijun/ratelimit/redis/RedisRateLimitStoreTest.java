package io.zhijun.ratelimit.redis;

import static org.assertj.core.api.Assertions.*;

import io.lettuce.core.RedisClient;
import io.zhijun.ratelimit.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisRateLimitStoreTest {
    static GenericContainer<?> redis;
    static RedisClient client;
    static RedisRateLimitStore first;
    static RedisRateLimitStore second;

    @BeforeAll
    static void start() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
        redis.start();
        client = RedisClient.create("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        first = new RedisRateLimitStore(() -> client, Duration.ofSeconds(2), 200);
        second = new RedisRateLimitStore(() -> client, Duration.ofSeconds(2), 200);
    }

    @AfterAll
    static void stop() {
        if (first != null) first.close();
        if (second != null) second.close();
        if (client != null) client.shutdown();
        if (redis != null) redis.stop();
    }

    @Test
    void independentInstancesShareAtomicQuota() throws Exception {
        String key = UUID.randomUUID().toString();
        var policy = new RateLimitPolicy(10, 1, Duration.ofDays(1));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 40; i++) {
                var store = i % 2 == 0 ? first : second;
                futures.add(executor.submit(() -> store.tryAcquire(key, policy).allowed()));
            }
            int allowed = 0;
            for (var future : futures) if (future.get()) allowed++;
            assertThat(allowed).isEqualTo(10);
        }
    }

    @Test
    void namespaceIsolationAndConfigurationFingerprintsAreConsistent() {
        String suffix = UUID.randomUUID().toString();
        var policy = new RateLimitPolicy(1, 1, Duration.ofDays(1));
        String a = RateLimitKeys.of("app-a", suffix, "client");
        String b = RateLimitKeys.of("app-b", suffix, "client");
        assertThat(first.tryAcquire(a, policy).allowed()).isTrue();
        assertThat(second.tryAcquire(a, policy).allowed()).isFalse();
        assertThat(second.tryAcquire(b, policy).allowed()).isTrue();
        assertThat(second.tryAcquire(a, new RateLimitPolicy(2, 1, Duration.ofDays(1)))
                        .allowed())
                .isTrue();
    }

    @Test
    void redisExpiryDoesNotResetDailyQuotaAfterOneHour() {
        String key = UUID.randomUUID().toString();
        var policy = new RateLimitPolicy(1, 1, Duration.ofDays(1));
        first.tryAcquire(key, policy);
        try (var connection = client.connect()) {
            long ttl = connection.sync().pttl("rose:ratelimit:v2:" + key + ":" + policy.fingerprint());
            assertThat(ttl).isGreaterThan(Duration.ofHours(23).toMillis());
        }
        assertThat(second.tryAcquire(key, policy).allowed()).isFalse();
    }

    @Test
    void unavailableServerFailsWithinTheConfiguredOperationBudget() {
        try (var store = new RedisRateLimitStore(() -> client, Duration.ofMillis(300), 2)) {
            var policy = new RateLimitPolicy(10, 1, Duration.ofDays(1));
            String key = UUID.randomUUID().toString();
            store.tryAcquire(key, policy);
            var docker = org.testcontainers.DockerClientFactory.instance().client();
            docker.pauseContainerCmd(redis.getContainerId()).exec();
            try {
                org.junit.jupiter.api.Assertions.assertTimeout(
                        Duration.ofSeconds(5),
                        () -> assertThatThrownBy(() -> store.tryAcquire(key, policy))
                                .isInstanceOf(RateLimitBackendException.class));
            } finally {
                docker.unpauseContainerCmd(redis.getContainerId()).exec();
            }
        }
    }

    @Test
    void disconnectedClientProducesTypedBackendFailure() {
        RedisClient stopped = RedisClient.create("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        stopped.shutdown();
        try (var store = new RedisRateLimitStore(() -> stopped, Duration.ofMillis(100), 2)) {
            assertThatThrownBy(() -> store.tryAcquire("offline", new RateLimitPolicy(1, 1, Duration.ofSeconds(1))))
                    .isInstanceOf(RateLimitBackendException.class);
        }
    }
}
