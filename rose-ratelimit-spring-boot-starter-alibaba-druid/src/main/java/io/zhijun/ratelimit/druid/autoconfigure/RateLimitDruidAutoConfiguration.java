package io.zhijun.ratelimit.druid.autoconfigure;

import com.alibaba.druid.pool.DruidDataSource;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.druid.*;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(DruidDataSource.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitDruidAutoConfiguration {
    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitJdbcExecutor rateLimitJdbcExecutor(RateLimitGuardedInvocation guardedInvocation) {
        return new RateLimitJdbcExecutor(guardedInvocation);
    }

    @Bean
    @ConditionalOnBean(RateLimitJdbcExecutor.class)
    RateLimitDruidFilter rateLimitDruidFilter(RateLimitRules rules, RateLimitJdbcExecutor executor) {
        return new RateLimitDruidFilter(rules, executor);
    }

    @Bean
    @ConditionalOnBean(RateLimitDruidFilter.class)
    RateLimitDruidBindingInitializer rateLimitDruidBindingInitializer(
            RateLimitRules rules,
            ApplicationContext context,
            RateLimitProperties properties,
            RateLimitDruidFilter filter) {
        return new RateLimitDruidBindingInitializer(rules, context, properties, filter);
    }
}
