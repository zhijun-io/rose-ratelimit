package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.*;
import jakarta.servlet.Filter;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(afterName = "io.zhijun.ratelimit.redis.autoconfigure.RedisRateLimitAutoConfiguration")
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "localRateLimitStore")
    LocalRateLimitStore localRateLimitStore(RateLimitProperties properties) {
        return new LocalRateLimitStore(properties.getLocal().getMaxBuckets());
    }

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
    RateLimitRules rateLimitRules(
            RateLimitProperties properties,
            List<RateLimitPlugin> plugins,
            Environment environment,
            ApplicationContext context) {
        return RateLimitRuleCompiler.compile(
                properties, plugins, environment.getProperty("spring.application.name"), context.getClassLoader());
    }

    @Bean
    RateLimitExecutor rateLimitExecutor(RateLimitRules rules, ObjectProvider<RateLimitObserver> observers) {
        return new RateLimitExecutor(rules, observers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "rose.ratelimit",
            name = "filter-enabled",
            havingValue = "true",
            matchIfMissing = true)
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties, RateLimitRules rules, RateLimitExecutor executor) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(rules, executor));
        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getFilterOrder());
        registration.setName("roseRateLimitFilter");
        registration.setAsyncSupported(true);
        return registration;
    }
}
