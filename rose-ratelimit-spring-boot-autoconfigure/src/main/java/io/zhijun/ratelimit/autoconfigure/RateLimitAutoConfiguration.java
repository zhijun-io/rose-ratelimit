package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.RateLimitExecutor;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitRules;
import io.zhijun.ratelimit.web.ResilienceBusinessDecorators;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RateLimitSharedAutoConfiguration.class)
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {
    @Bean
    RateLimitExecutor rateLimitExecutor(RateLimitGuardedInvocation guardedInvocation) {
        return new RateLimitExecutor(guardedInvocation);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "rose.ratelimit",
            name = "filter-enabled",
            havingValue = "true",
            matchIfMissing = true)
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties,
            RateLimitRules rules,
            RateLimitExecutor executor,
            ResilienceBusinessDecorators businessDecorators) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(rules, executor, businessDecorators));
        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getFilterOrder());
        registration.setName("roseRateLimitFilter");
        registration.setAsyncSupported(true);
        return registration;
    }
}
