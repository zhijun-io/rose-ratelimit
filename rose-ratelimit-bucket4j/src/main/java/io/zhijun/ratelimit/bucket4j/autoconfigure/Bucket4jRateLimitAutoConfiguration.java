package io.zhijun.ratelimit.bucket4j.autoconfigure;

import io.zhijun.ratelimit.bucket4j.LocalRateLimitStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Bucket4jRateLimitAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "localRateLimitStore")
    LocalRateLimitStore localRateLimitStore(Environment environment) {
        int maxBuckets = Binder.get(environment)
                .bind("rose.ratelimit.local.max-buckets", Integer.class)
                .orElse(100_000);
        return new LocalRateLimitStore(maxBuckets);
    }
}
