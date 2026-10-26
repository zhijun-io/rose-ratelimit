package io.zhijun.ratelimit.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.cluster.RedisClusterClient;
import io.zhijun.ratelimit.RateLimitPolicy;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class RedisClusterRateLimitStoreTest {
    private static final int HOST_PORT = 17300;
    private static final int[] REDIS_PORTS = {17300, 17301, 17302};
    private static GenericContainer<?> redis;
    private static RedisClusterClient client;
    private static RedisRateLimitStore first;
    private static RedisRateLimitStore second;

    @BeforeAll
    static void startSingleNodeCluster() throws Exception {
        redis = new FixedPortRedisContainer()
                .withCommand(
                        "sh",
                        "-c",
                        redisCommand(REDIS_PORTS[0]) + " && " + redisCommand(REDIS_PORTS[1]) + " && "
                                + redisCommand(REDIS_PORTS[2]) + " && sleep infinity")
                .waitingFor(Wait.forListeningPorts());
        redis.start();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            for (int port : REDIS_PORTS) {
                var ping = redis.execInContainer("redis-cli", "-p", Integer.toString(port), "PING");
                assertThat(ping.getStdout()).contains("PONG");
            }
        });
        var result = redis.execInContainer(
                "redis-cli",
                "--cluster",
                "create",
                "127.0.0.1:17300",
                "127.0.0.1:17301",
                "127.0.0.1:17302",
                "--cluster-replicas",
                "0",
                "--cluster-yes");
        assertThat(result.getExitCode())
                .withFailMessage("exit=%s\n%s%s", result.getExitCode(), result.getStdout(), result.getStderr())
                .isZero();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            for (int port : REDIS_PORTS) {
                var clusterInfo = redis.execInContainer("redis-cli", "-p", Integer.toString(port), "CLUSTER", "INFO");
                assertThat(clusterInfo.getStdout()).contains("cluster_state:ok");
            }
        });

        client = RedisClusterClient.create("redis://127.0.0.1:17300");
        first = new RedisRateLimitStore(() -> client, Duration.ofSeconds(2), 8);
        second = new RedisRateLimitStore(() -> client, Duration.ofSeconds(2), 8);
    }

    private static final class FixedPortRedisContainer extends GenericContainer<FixedPortRedisContainer> {
        private FixedPortRedisContainer() {
            super(DockerImageName.parse("redis:7.4-alpine"));
        }

        @Override
        protected void configure() {
            for (int port : REDIS_PORTS) {
                addFixedExposedPort(port, port);
                addFixedExposedPort(port + 10_000, port + 10_000);
            }
            super.configure();
        }
    }

    private static String redisCommand(int port) {
        return "redis-server --port " + port + " --cluster-enabled yes --cluster-config-file /tmp/nodes-" + port
                + ".conf --cluster-node-timeout 5000 --cluster-announce-ip 127.0.0.1 --cluster-announce-port "
                + port + " --cluster-announce-bus-port " + (port + 10_000) + " --protected-mode no --daemonize yes";
    }

    @AfterAll
    static void stop() {
        if (first != null) first.close();
        if (second != null) second.close();
        if (client != null) client.shutdown();
        if (redis != null) redis.stop();
    }

    @Test
    void bucket4jClusterAdapterSharesQuotaBetweenIndependentStores() {
        String key = UUID.randomUUID().toString();
        var policy = new RateLimitPolicy(2, 1, Duration.ofDays(1));
        assertThat(first.tryAcquire(key, policy).allowed()).isTrue();
        assertThat(second.tryAcquire(key, policy).allowed()).isTrue();
        assertThat(first.tryAcquire(key, policy).allowed()).isFalse();
    }

    @Test
    void redisAdapterClosesItsOwnClusterConnection() {
        String key = UUID.randomUUID().toString();
        var policy = new RateLimitPolicy(1, 1, Duration.ofDays(1));
        try (var store = new RedisRateLimitStore(() -> client, Duration.ofSeconds(2), 8)) {
            assertThat(store.tryAcquire(key, policy).allowed()).isTrue();
            store.close();
            assertThatThrownBy(() -> store.tryAcquire(key, policy))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
    }
}
