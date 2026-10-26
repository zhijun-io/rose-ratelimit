package io.zhijun.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ResilienceContractsTest {
    @Test
    void immutableBehaviorSpecsValidateInputsAndRetainValues() {
        assertThat(new BulkheadSpec(4, Duration.ZERO).maxConcurrentCalls()).isEqualTo(4);
        assertThat(new RetrySpec(3, Duration.ofMillis(25)).waitDuration()).isEqualTo(Duration.ofMillis(25));
        assertThat(new TimeLimiterSpec(Duration.ofSeconds(2), true).cancelRunningFuture())
                .isTrue();
        assertThat(new CircuitBreakerSpec(50, 40, Duration.ofSeconds(3), 5, 10, Duration.ofSeconds(2), 2)
                        .failureRateThreshold())
                .isEqualTo(50);

        assertThatThrownBy(() -> new BulkheadSpec(0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BulkheadSpec(1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySpec(0, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySpec(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeLimiterSpec(Duration.ZERO, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerSpec(101, 50, Duration.ZERO, 1, 1, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerSpec(50, -1, Duration.ZERO, 1, 1, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerSpec(50, 50, Duration.ZERO, 0, 1, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outcomesAndDecisionsExposeTheirTransportIndependentResults() {
        assertThat(RateLimitDecision.allowed(2)).isEqualTo(new RateLimitDecision(true, 2, Duration.ZERO));
        assertThat(RateLimitDecision.rejected(Duration.ofSeconds(1)))
                .isEqualTo(new RateLimitDecision(false, 0, Duration.ofSeconds(1)));
        assertThat(RoseResilienceOutcome.allowed().result()).isEqualTo(RoseResilienceOutcome.Result.ALLOWED);
        assertThat(RoseResilienceOutcome.rejected(Duration.ofSeconds(1)).retryAfter())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(RoseResilienceOutcome.missingIdentity().result())
                .isEqualTo(RoseResilienceOutcome.Result.MISSING_IDENTITY);
        assertThat(RoseResilienceOutcome.backendOpen().result()).isEqualTo(RoseResilienceOutcome.Result.BACKEND_OPEN);
        assertThat(RoseResilienceOutcome.backendClosed().result())
                .isEqualTo(RoseResilienceOutcome.Result.BACKEND_CLOSED);
        assertThat(RoseResilienceOutcome.circuitOpen().result()).isEqualTo(RoseResilienceOutcome.Result.CIRCUIT_OPEN);
        assertThat(ResilienceRuleKind.values())
                .containsExactly(
                        ResilienceRuleKind.RATE_LIMIT,
                        ResilienceRuleKind.CIRCUIT_BREAKER,
                        ResilienceRuleKind.COMPOSITE);
        assertThat(ResilienceBehavior.values())
                .containsExactly(
                        ResilienceBehavior.RATE_LIMIT,
                        ResilienceBehavior.CIRCUIT_BREAKER,
                        ResilienceBehavior.RETRY,
                        ResilienceBehavior.TIME_LIMITER,
                        ResilienceBehavior.BULKHEAD);
    }

    @Test
    void backendExceptionPreservesOptionalCause() {
        var cause = new IllegalStateException("backend unavailable");
        assertThat(new RateLimitBackendException("failed").getMessage()).isEqualTo("failed");
        assertThat(new RateLimitBackendException("failed", cause).getCause()).isSameAs(cause);
    }
}
