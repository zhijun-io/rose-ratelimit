package io.zhijun.ratelimit.mybatis.autoconfigure;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.mybatis.*;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitMybatisAutoConfiguration {
    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitMybatisExecutor rateLimitMybatisExecutor(RateLimitGuardedInvocation guardedInvocation) {
        return new RateLimitMybatisExecutor(guardedInvocation);
    }

    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitMybatisBindingInitializer rateLimitMybatisBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        return new RateLimitMybatisBindingInitializer(rules, context, properties);
    }

    @Bean
    @ConditionalOnBean(RateLimitMybatisExecutor.class)
    RateLimitMybatisInterceptor rateLimitMybatisInterceptor(
            RateLimitRules rules, RateLimitMybatisExecutor executor, RateLimitProperties properties) {
        return new RateLimitMybatisInterceptor(rules, executor, properties);
    }

    @Bean
    @ConditionalOnBean(RateLimitMybatisInterceptor.class)
    static RateLimitMybatisInterceptorRegistrar rateLimitMybatisInterceptorRegistrar(
            RateLimitMybatisInterceptor interceptor) {
        return new RateLimitMybatisInterceptorRegistrar(interceptor);
    }
}
