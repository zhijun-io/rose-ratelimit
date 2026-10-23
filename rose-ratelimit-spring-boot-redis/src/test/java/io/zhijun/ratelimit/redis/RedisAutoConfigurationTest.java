package io.zhijun.ratelimit.redis;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.redis.autoconfigure.RedisRateLimitAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataRedisAutoConfiguration.class,
                    RedisRateLimitAutoConfiguration.class,
                    RateLimitAutoConfiguration.class));

    @Test
    void redisPluginIsDiscoveredWithoutOpeningAConnection() {
        runner.withPropertyValues("rose.ratelimit.store.type=redis", "spring.application.name=app")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(RedisRateLimitStore.class)
                        .hasSingleBean(RateLimitExecutor.class)
                        .hasSingleBean(LettuceConnectionFactory.class));
    }

    @Test
    void sentinelConfigurationCreatesTheSentinelAwareLettuceClient() {
        runner.withPropertyValues(
                        "rose.ratelimit.store.type=redis",
                        "spring.application.name=app",
                        "spring.data.redis.sentinel.master=mymaster",
                        "spring.data.redis.sentinel.nodes=localhost:26379")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(LettuceConnectionFactory.class)
                        .extracting(LettuceConnectionFactory::isRedisSentinelAware)
                        .isEqualTo(true));
    }

    @Test
    void globalDisableAlsoDisablesRedisPlugin() {
        runner.withPropertyValues("rose.ratelimit.enabled=false", "rose.ratelimit.store.type=redis")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(RedisRateLimitStore.class)
                        .doesNotHaveBean(RateLimitExecutor.class));
    }

    @Test
    void redisNeedsNamespaceAndValidTimeout() {
        runner.withPropertyValues("rose.ratelimit.store.type=redis")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("namespace"));
        runner.withPropertyValues("spring.application.name=app", "rose.ratelimit.redis.timeout=0s")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("must be positive"));
    }
}
