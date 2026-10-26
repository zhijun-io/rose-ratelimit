package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Resilience4jBehaviorRegistryTest {
    @Test
    void retriesFlakyCallableUntilSuccess() throws Exception {
        CompiledRule rule = invokeRule("charge", new RetrySpec(3, Duration.ofMillis(1)), null, null);
        Resilience4jBehaviorRegistry registry =
                new Resilience4jBehaviorRegistry(new RateLimitRules("app", List.of(rule)));
        AtomicInteger attempts = new AtomicInteger();
        String value = registry.executeRetry("charge", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("flaky");
            }
            return "ok";
        });
        assertThat(value).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void ruleWithoutRetryDelegatesDirectly() throws Exception {
        CompiledRule rule = invokeRule("plain", null, null, null);
        Resilience4jBehaviorRegistry registry =
                new Resilience4jBehaviorRegistry(new RateLimitRules("app", List.of(rule)));
        AtomicInteger attempts = new AtomicInteger();
        assertThat(registry.executeRetry("plain", () -> attempts.incrementAndGet()))
                .isEqualTo(1);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void slowCallableTimesOut() {
        TimeLimiterSpec spec = new TimeLimiterSpec(Duration.ofMillis(50), true);
        CompiledRule rule = invokeRule("slow", null, spec, null);
        Resilience4jBehaviorRegistry registry =
                new Resilience4jBehaviorRegistry(new RateLimitRules("app", List.of(rule)));
        assertThatThrownBy(() -> registry.executeTimeLimiter("slow", () -> {
                    Thread.sleep(500);
                    return "late";
                }))
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    void fastCallableCompletes() throws Exception {
        TimeLimiterSpec spec = new TimeLimiterSpec(Duration.ofSeconds(2), true);
        CompiledRule rule = invokeRule("fast", null, spec, null);
        Resilience4jBehaviorRegistry registry =
                new Resilience4jBehaviorRegistry(new RateLimitRules("app", List.of(rule)));
        assertThat(registry.executeTimeLimiter("fast", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void secondConcurrentCallRejectedWhenBulkheadFull() throws Exception {
        BulkheadSpec spec = new BulkheadSpec(1, Duration.ZERO);
        CompiledRule rule = invokeRule("bh", null, null, spec);
        Resilience4jBehaviorRegistry registry =
                new Resilience4jBehaviorRegistry(new RateLimitRules("app", List.of(rule)));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> secondError = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                registry.executeBulkhead("bh", () -> {
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return "first";
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread second = new Thread(() -> {
            try {
                registry.executeBulkhead("bh", () -> "second");
            } catch (Throwable t) {
                secondError.set(t);
            }
        });
        second.start();
        second.join(5000);
        release.countDown();
        first.join(5000);
        assertThat(secondError.get()).isInstanceOf(BulkheadFullException.class);
    }

    private static CompiledRule invokeRule(
            String id, RetrySpec retry, TimeLimiterSpec timeLimiter, BulkheadSpec bulkhead) {
        RateLimitStore store = new RateLimitStore() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                return RateLimitDecision.allowed(1);
            }
        };
        RateLimitKeyResolver resolver = new RateLimitKeyResolver() {
            @Override
            public String name() {
                return "invoke";
            }

            @Override
            public String resolve(jakarta.servlet.http.HttpServletRequest request, String headerName) {
                return "identity";
            }
        };
        return new CompiledRule(
                id,
                true,
                Stage.INVOKE,
                null,
                Set.of(),
                "*Service#charge",
                Scope.HANDLER,
                new RateLimitPolicy(100, 100, Duration.ofDays(1)),
                store,
                resolver,
                null,
                MissingKey.REJECT,
                FailureMode.OPEN,
                429,
                "application/problem+json",
                "{}",
                ResilienceRuleKind.COMPOSITE,
                null,
                503,
                retry,
                timeLimiter,
                bulkhead);
    }
}
