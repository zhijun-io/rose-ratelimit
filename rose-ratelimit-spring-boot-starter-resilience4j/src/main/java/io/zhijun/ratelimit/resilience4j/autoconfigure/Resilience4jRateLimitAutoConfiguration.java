package io.zhijun.ratelimit.resilience4j.autoconfigure;

import io.zhijun.ratelimit.ResilienceBehaviorRegistry;
import io.zhijun.ratelimit.ResiliencePipelineRegistry;
import io.zhijun.ratelimit.resilience4j.Resilience4jBehaviorRegistry;
import io.zhijun.ratelimit.resilience4j.Resilience4jPipelineRegistry;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreaker")
@ConditionalOnBean(RateLimitRules.class)
public class Resilience4jRateLimitAutoConfiguration {
    @Bean
    ResiliencePipelineRegistry resilience4jPipelineRegistry(RateLimitRules rules) {
        return new Resilience4jPipelineRegistry(rules);
    }

    @Bean
    @ConditionalOnClass(name = "io.github.resilience4j.retry.Retry")
    ResilienceBehaviorRegistry resilience4jBehaviorRegistry(RateLimitRules rules) {
        return new Resilience4jBehaviorRegistry(rules);
    }
}
