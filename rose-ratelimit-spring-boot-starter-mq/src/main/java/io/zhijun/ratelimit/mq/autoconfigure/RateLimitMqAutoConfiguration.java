package io.zhijun.ratelimit.mq.autoconfigure;

import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.mq.MqRateLimitKeyResolver;
import io.zhijun.ratelimit.mq.RateLimitMqExecutor;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RateLimitSharedAutoConfiguration.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitMqAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "mqRateLimitKeyResolver")
    RateLimitKeyResolver mqRateLimitKeyResolver() {
        return new MqRateLimitKeyResolver();
    }

    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    @ConditionalOnMissingBean
    RateLimitMqExecutor rateLimitMqExecutor(RateLimitGuardedInvocation guardedInvocation) {
        return new RateLimitMqExecutor(guardedInvocation);
    }
}
