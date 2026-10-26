package io.zhijun.ratelimit.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.zhijun.ratelimit.CircuitBreakerSpec;
import io.zhijun.ratelimit.ResiliencePipeline;
import io.zhijun.ratelimit.ResiliencePipelineRegistry;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class Resilience4jPipelineRegistry implements ResiliencePipelineRegistry {
    private final Map<String, ResiliencePipeline> pipelines;

    public Resilience4jPipelineRegistry(RateLimitRules rules) {
        Map<String, ResiliencePipeline> built = new HashMap<>();
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || !rule.usesCircuitBreaker() || rule.circuitBreaker() == null) continue;
            String name = rules.namespace() + ":" + rule.id() + ":cb";
            CircuitBreaker breaker = CircuitBreaker.of(name, toConfig(rule.circuitBreaker()));
            built.put(rule.id(), new Resilience4jCircuitBreakerPipeline(breaker));
        }
        pipelines = Map.copyOf(built);
    }

    @Override
    public Optional<ResiliencePipeline> pipelineFor(String ruleId) {
        return Optional.ofNullable(pipelines.get(ruleId));
    }

    private static CircuitBreakerConfig toConfig(CircuitBreakerSpec spec) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(spec.failureRateThreshold())
                .slowCallRateThreshold(spec.slowCallRateThreshold())
                .slowCallDurationThreshold(spec.slowCallDurationThreshold())
                .minimumNumberOfCalls(spec.minimumNumberOfCalls())
                .slidingWindowSize(spec.slidingWindowSize())
                .waitDurationInOpenState(spec.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(spec.permittedNumberOfCallsInHalfOpenState())
                .build();
    }
}
