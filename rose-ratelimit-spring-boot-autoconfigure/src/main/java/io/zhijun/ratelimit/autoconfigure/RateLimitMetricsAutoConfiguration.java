package io.zhijun.ratelimit.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.zhijun.ratelimit.RateLimitObserver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        before = RateLimitAutoConfiguration.class,
        afterName = "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration")
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitMetricsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "rateLimitMetricsObserver")
    RateLimitObserver rateLimitMetricsObserver(MeterRegistry registry) {
        return (rule, backend, result) -> registry.counter(
                        "rose.ratelimit.requests", "rule", rule, "backend", backend, "result", result)
                .increment();
    }
}
