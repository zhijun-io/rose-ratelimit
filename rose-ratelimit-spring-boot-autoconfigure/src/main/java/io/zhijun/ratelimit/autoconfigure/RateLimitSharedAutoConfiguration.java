package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.*;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Rules compilation and programmatic {@link RoseResilience} (no Servlet required). */
@AutoConfiguration(afterName = "io.zhijun.ratelimit.redis.autoconfigure.RedisRateLimitAutoConfiguration")
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitSharedAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "ipRateLimitKeyResolver")
    RateLimitKeyResolver ipRateLimitKeyResolver() {
        return new DefaultRateLimitKeyResolver("ip");
    }

    @Bean
    @ConditionalOnMissingBean(name = "principalRateLimitKeyResolver")
    RateLimitKeyResolver principalRateLimitKeyResolver() {
        return new DefaultRateLimitKeyResolver("principal");
    }

    @Bean
    @ConditionalOnMissingBean(name = "headerRateLimitKeyResolver")
    RateLimitKeyResolver headerRateLimitKeyResolver() {
        return new DefaultRateLimitKeyResolver("header");
    }

    @Bean
    @ConditionalOnMissingBean(name = "invokeRateLimitKeyResolver")
    RateLimitKeyResolver invokeRateLimitKeyResolver() {
        return new InvokeRateLimitKeyResolver();
    }

    @Bean
    RateLimitRules rateLimitRules(
            RateLimitProperties properties,
            List<RateLimitPlugin> plugins,
            Environment environment,
            ApplicationContext context) {
        return RateLimitRuleCompiler.compile(
                properties, plugins, environment.getProperty("spring.application.name"), context.getClassLoader());
    }

    @Bean
    ResilienceBusinessDecorators resilienceBusinessDecorators(ObjectProvider<ResilienceBehaviorRegistry> behaviors) {
        return new ResilienceBusinessDecorators(behaviors);
    }

    @Bean
    RateLimitGuardedInvocation rateLimitGuardedInvocation(
            RateLimitRules rules,
            ObjectProvider<RateLimitObserver> observers,
            ObjectProvider<ResiliencePipelineRegistry> pipelines,
            ResilienceBusinessDecorators businessDecorators) {
        return new RateLimitGuardedInvocation(rules, observers.orderedStream().toList(), pipelines, businessDecorators);
    }

    @Bean
    RateLimitInvokeExecutor rateLimitInvokeExecutor(RateLimitGuardedInvocation guardedInvocation) {
        return new RateLimitInvokeExecutor(guardedInvocation);
    }

    @Bean
    RoseResilience roseResilience(
            RateLimitRules rules, RateLimitInvokeExecutor invokeExecutor, RateLimitProperties properties) {
        return new RoseResilience(rules, invokeExecutor, properties);
    }
}
