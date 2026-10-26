package io.zhijun.ratelimit.resilience4j;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.zhijun.ratelimit.RateLimitBackendException;
import io.zhijun.ratelimit.ResiliencePipeline;
import io.zhijun.ratelimit.RoseResilienceInvocation;
import io.zhijun.ratelimit.RoseResilienceOutcome;
import io.zhijun.ratelimit.RoseResilienceRequest;

final class Resilience4jCircuitBreakerPipeline implements ResiliencePipeline {
    private final CircuitBreaker circuitBreaker;
    private final RoseResilienceInvocation invocation = new RoseResilienceInvocation();

    Resilience4jCircuitBreakerPipeline(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public RoseResilienceOutcome invoke(RoseResilienceRequest request) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                RoseResilienceOutcome outcome = invocation.invoke(request);
                if (outcome.result() == RoseResilienceOutcome.Result.BACKEND_CLOSED) {
                    circuitBreaker.onError(
                            0, java.util.concurrent.TimeUnit.NANOSECONDS, new RateLimitBackendException("backend"));
                }
                return outcome;
            });
        } catch (CallNotPermittedException e) {
            return RoseResilienceOutcome.circuitOpen();
        }
    }
}
