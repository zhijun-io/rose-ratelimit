package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.zhijun.ratelimit.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Resilience4jCircuitBreakerPipelineTest {
    @Test
    void openCircuitReturnsCircuitOpenWithoutCallingStore() {
        CircuitBreaker breaker = CircuitBreaker.of(
                "test",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build());
        breaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        breaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException("fail"));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        var pipeline = new Resilience4jCircuitBreakerPipeline(breaker);
        var request = new RoseResilienceRequest(
                "key",
                new RateLimitPolicy(1, 1, Duration.ofSeconds(1)),
                new RateLimitStore() {
                    public String name() {
                        return "must-not-run";
                    }

                    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                        throw new AssertionError("store must not run when circuit is open");
                    }
                },
                RoseResilienceRequest.MissingKeyPolicy.REJECT,
                "client",
                RoseResilienceRequest.FailureMode.CLOSED);

        assertThat(pipeline.invoke(request).result()).isEqualTo(RoseResilienceOutcome.Result.CIRCUIT_OPEN);
    }
}
