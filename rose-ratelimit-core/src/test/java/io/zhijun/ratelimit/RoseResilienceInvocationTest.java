package io.zhijun.ratelimit;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RoseResilienceInvocationTest {
    private final RoseResilienceInvocation invocation = new RoseResilienceInvocation();

    @Test
    void missingIdentityRejectedWhenPolicySaysSo() {
        var request = new RoseResilienceRequest(
                "key",
                new RateLimitPolicy(1, 1, Duration.ofSeconds(1)),
                new RateLimitStore() {
                    public String name() {
                        return "noop";
                    }

                    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                        throw new AssertionError("store must not run");
                    }
                },
                RoseResilienceRequest.MissingKeyPolicy.REJECT,
                "",
                RoseResilienceRequest.FailureMode.OPEN);
        assertThat(invocation.invoke(request).result()).isEqualTo(RoseResilienceOutcome.Result.MISSING_IDENTITY);
    }

    @Test
    void backendOpenAllowsTraffic() {
        var request = new RoseResilienceRequest(
                "key",
                new RateLimitPolicy(1, 1, Duration.ofSeconds(1)),
                new RateLimitStore() {
                    public String name() {
                        return "broken";
                    }

                    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                        throw new RateLimitBackendException("down");
                    }
                },
                RoseResilienceRequest.MissingKeyPolicy.SHARED,
                "client",
                RoseResilienceRequest.FailureMode.OPEN);
        assertThat(invocation.invoke(request).result()).isEqualTo(RoseResilienceOutcome.Result.BACKEND_OPEN);
    }

    @Test
    void quotaRejectionReturnsRetryAfter() {
        var request = new RoseResilienceRequest(
                "key",
                new RateLimitPolicy(1, 1, Duration.ofSeconds(2)),
                new RateLimitStore() {
                    public String name() {
                        return "probe";
                    }

                    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                        return RateLimitDecision.rejected(Duration.ofMillis(1500));
                    }
                },
                RoseResilienceRequest.MissingKeyPolicy.REJECT,
                "client",
                RoseResilienceRequest.FailureMode.CLOSED);
        var outcome = invocation.invoke(request);
        assertThat(outcome.result()).isEqualTo(RoseResilienceOutcome.Result.REJECTED);
        assertThat(outcome.retryAfter()).isEqualTo(Duration.ofMillis(1500));
    }
}
