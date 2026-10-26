package io.zhijun.ratelimit.feign.autoconfigure;

import io.zhijun.ratelimit.feign.FeignRateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(FeignClient.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFeignResolverAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "feignRateLimitKeyResolver")
    RateLimitKeyResolver feignRateLimitKeyResolver() {
        return new FeignRateLimitKeyResolver();
    }
}
