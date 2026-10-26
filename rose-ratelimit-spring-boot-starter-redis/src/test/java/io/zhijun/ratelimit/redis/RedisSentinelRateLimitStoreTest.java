package io.zhijun.ratelimit.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.zhijun.ratelimit.RateLimitBackendException;
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

/** Exercises a Sentinel promotion and verifies that the store reconnects to the promoted master. */
class RedisSentinelRateLimitStoreTest {
    private static final int PRIMARY_PORT = 16379;
    private static final int REPLICA_PORT = 16380;
    private static final int SENTINEL_PORT = 26379;
    private static GenericContainer<?> redis;
    private static RedisClient client;
    private static RedisRateLimitStore store;

    @BeforeAll
    static void start() {
        redis = new FixedPortRedisContainer()
                .withCommand("sh", "-c", startupCommand())
                .waitingFor(Wait.forListeningPorts());
        redis.start();
        var sentinelUri = RedisURI.Builder.sentinel("127.0.0.1", SENTINEL_PORT, "mymaster")
                .build();
        client = RedisClient.create(sentinelUri);
        store = new RedisRateLimitStore(() -> client, Duration.ofSeconds(2), 8);
    }

    @AfterAll
    static void stop() {
        if (store != null) store.close();
        if (client != null) client.shutdown();
        if (redis != null) redis.stop();
    }

    @Test
    void reconnectsAfterSentinelPromotesTheReplica() throws Exception {
        String key = UUID.randomUUID().toString();
        var policy = new RateLimitPolicy(1, 1, Duration.ofDays(1));
        assertThat(store.tryAcquire(key, policy).allowed()).isTrue();

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var replication =
                    redis.execInContainer("redis-cli", "-p", Integer.toString(PRIMARY_PORT), "WAIT", "1", "1000");
            assertThat(replication.getStdout()).contains("1");
        });

        var shutdown = redis.execInContainer("redis-cli", "-p", Integer.toString(PRIMARY_PORT), "SHUTDOWN", "NOSAVE");
        assertThat(shutdown.getExitCode()).isIn(0, 1);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var master = redis.execInContainer(
                    "redis-cli",
                    "-p",
                    Integer.toString(SENTINEL_PORT),
                    "SENTINEL",
                    "GET-MASTER-ADDR-BY-NAME",
                    "mymaster");
            assertThat(master.getStdout()).contains(Integer.toString(REPLICA_PORT));
        });

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> {
            try {
                return !store.tryAcquire(key, policy).allowed();
            } catch (RateLimitBackendException ignoredDuringReconnect) {
                // The failed request is not replayed; the store discards the dead connection for the next request.
                return false;
            }
        });
    }

    private static String startupCommand() {
        String sentinelConfig = "port " + SENTINEL_PORT + "\n"
                + "bind 0.0.0.0\n"
                + "protected-mode no\n"
                + "sentinel monitor mymaster 127.0.0.1 " + PRIMARY_PORT + " 1\n"
                + "sentinel down-after-milliseconds mymaster 1000\n"
                + "sentinel failover-timeout mymaster 10000\n"
                + "sentinel parallel-syncs mymaster 1\n"
                + "sentinel announce-ip 127.0.0.1\n"
                + "sentinel announce-port " + SENTINEL_PORT + "\n"
                + "daemonize yes\n"
                + "pidfile /tmp/sentinel.pid\n"
                + "logfile /tmp/sentinel.log\n";
        return "redis-server --port " + PRIMARY_PORT + " --protected-mode no --daemonize yes --pidfile /tmp/primary.pid"
                + " && redis-server --port " + REPLICA_PORT + " --replicaof 127.0.0.1 " + PRIMARY_PORT
                + " --replica-announce-ip 127.0.0.1 --replica-announce-port " + REPLICA_PORT
                + " --protected-mode no --daemonize yes --pidfile /tmp/replica.pid"
                + " && printf '%s' '" + sentinelConfig + "' > /tmp/sentinel.conf"
                + " && redis-server /tmp/sentinel.conf --sentinel && sleep infinity";
    }

    private static final class FixedPortRedisContainer extends GenericContainer<FixedPortRedisContainer> {
        private FixedPortRedisContainer() {
            super(DockerImageName.parse("redis:7.4-alpine"));
        }

        @Override
        protected void configure() {
            addFixedExposedPort(PRIMARY_PORT, PRIMARY_PORT);
            addFixedExposedPort(REPLICA_PORT, REPLICA_PORT);
            addFixedExposedPort(SENTINEL_PORT, SENTINEL_PORT);
            super.configure();
        }
    }
}
