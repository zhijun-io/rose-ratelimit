package io.zhijun.ratelimit.feign.autoconfigure;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.feign.*;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(FeignClient.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFeignAutoConfiguration {
    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitFeignExecutor rateLimitFeignExecutor(RateLimitGuardedInvocation guardedInvocation) {
        return new RateLimitFeignExecutor(guardedInvocation);
    }

    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitFeignBindingInitializer rateLimitFeignBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        return new RateLimitFeignBindingInitializer(rules, context, properties);
    }

    @Bean
    @ConditionalOnBean(RateLimitFeignExecutor.class)
    FeignBuilderCustomizer rateLimitFeignBuilderCustomizer(
            RateLimitRules rules, RateLimitFeignExecutor executor, RateLimitProperties properties) {
        return builder -> builder.invocationHandlerFactory(
                new RateLimitFeignInvocationHandlerFactory(rules, executor, properties));
    }
}
