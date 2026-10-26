package io.zhijun.ratelimit.mybatis.autoconfigure;

import io.zhijun.ratelimit.mybatis.MybatisRateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitMybatisResolverAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "mybatisRateLimitKeyResolver")
    RateLimitKeyResolver mybatisRateLimitKeyResolver() {
        return new MybatisRateLimitKeyResolver();
    }
}
