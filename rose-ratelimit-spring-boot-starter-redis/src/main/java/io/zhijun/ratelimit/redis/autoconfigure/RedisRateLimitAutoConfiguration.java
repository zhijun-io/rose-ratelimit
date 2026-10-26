package io.zhijun.ratelimit.redis.autoconfigure;

import io.zhijun.ratelimit.redis.RedisRateLimitStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@AutoConfiguration(
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        beforeName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@EnableConfigurationProperties(RedisRateLimitProperties.class)
@ConditionalOnClass(LettuceConnectionFactory.class)
@ConditionalOnBean(LettuceConnectionFactory.class)
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimitAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "redisRateLimitStore")
    RedisRateLimitStore redisRateLimitStore(LettuceConnectionFactory factory, RedisRateLimitProperties properties) {
        return new RedisRateLimitStore(factory::getNativeClient, properties.timeout(), properties.maxRetries());
    }
}
