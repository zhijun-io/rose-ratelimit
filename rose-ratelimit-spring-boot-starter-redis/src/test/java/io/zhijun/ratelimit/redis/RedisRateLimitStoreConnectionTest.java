package io.zhijun.ratelimit.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.zhijun.ratelimit.RateLimitBackendException;
import io.zhijun.ratelimit.RateLimitPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** §9.3 T-REDIS-CONN-01 — connect 成功、后续建连步骤失败时关闭连接。 */
class RedisRateLimitStoreConnectionTest {
    @Test
    @SuppressWarnings("unchecked")
    void tRedisConn01_closesConnectionWhenProxyManagerBuildFails() {
        StatefulRedisConnection<String, byte[]> connection = mock(StatefulRedisConnection.class);
        RedisClient client = mock(RedisClient.class);
        when(client.connect(any(RedisCodec.class))).thenReturn(connection);
        doThrow(new RedisException("setup failed")).when(connection).setTimeout(any(Duration.class));

        var store = new RedisRateLimitStore(() -> client, Duration.ofSeconds(1), 1);
        var policy = new RateLimitPolicy(1, 1, Duration.ofSeconds(1));

        assertThatThrownBy(() -> store.tryAcquire("key", policy)).isInstanceOf(RateLimitBackendException.class);
        verify(connection).close();
    }
}
