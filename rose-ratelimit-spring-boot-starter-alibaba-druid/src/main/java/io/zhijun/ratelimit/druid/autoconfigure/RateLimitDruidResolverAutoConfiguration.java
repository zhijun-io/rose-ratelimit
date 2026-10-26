package io.zhijun.ratelimit.druid.autoconfigure;

import com.alibaba.druid.pool.DruidDataSource;
import io.zhijun.ratelimit.druid.JdbcRateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(DruidDataSource.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitDruidResolverAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "jdbcRateLimitKeyResolver")
    RateLimitKeyResolver jdbcRateLimitKeyResolver() {
        return new JdbcRateLimitKeyResolver();
    }
}
