package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import io.zhijun.ratelimit.web.RateLimitRules;
import io.zhijun.ratelimit.web.ResilienceBusinessDecorators;
import io.zhijun.ratelimit.web.ResilienceHttpFilterChain;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ResilienceHttpFilterChainTest {
    @Test
    void bulkheadOnFilterRuleLimitsConcurrentChainContinuations() throws Exception {
        BulkheadSpec spec = new BulkheadSpec(1, Duration.ZERO);
        CompiledRule rule = filterRule("api", spec);
        Resilience4jBehaviorRegistry behaviors =
                new Resilience4jBehaviorRegistry(new RateLimitRules("app", List.of(rule)));
        ResilienceBusinessDecorators decorators = new ResilienceBusinessDecorators(provider(behaviors));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FilterChain chain = (request, response) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new jakarta.servlet.ServletException("release timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new jakarta.servlet.ServletException(e);
            }
        };
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                ResilienceHttpFilterChain.doFilter(
                        decorators,
                        rule,
                        chain,
                        new MockHttpServletRequest("GET", "/api/hold"),
                        new MockHttpServletResponse());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        holder.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        Thread challenger = new Thread(() -> {
            try {
                ResilienceHttpFilterChain.doFilter(
                        decorators,
                        rule,
                        chain,
                        new MockHttpServletRequest("GET", "/api/hold"),
                        new MockHttpServletResponse());
            } catch (Throwable t) {
                rejected.set(t);
            }
        });
        challenger.start();
        challenger.join(5000);
        release.countDown();
        holder.join(5000);
        assertThat(rejected.get()).isInstanceOf(BulkheadFullException.class);
    }

    private static ObjectProvider<ResilienceBehaviorRegistry> provider(ResilienceBehaviorRegistry registry) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ResilienceBehaviorRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    private static CompiledRule filterRule(String id, BulkheadSpec bulkhead) {
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
                return "ip";
            }

            @Override
            public String resolve(jakarta.servlet.http.HttpServletRequest request, String headerName) {
                return "127.0.0.1";
            }
        };
        return new CompiledRule(
                id,
                true,
                Stage.FILTER,
                new org.springframework.web.util.pattern.PathPatternParser().parse("/api/hold"),
                Set.of(),
                null,
                Scope.RULE,
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
                null,
                null,
                bulkhead);
    }
}
