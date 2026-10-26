package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.resilience4j.autoconfigure.Resilience4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.web.RoseResilience;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class RoseResilienceInvokeBulkheadTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    Resilience4jRateLimitAutoConfiguration.class));

    @Test
    void compositeBulkheadLimitsConcurrentInvoke() {
        runner.withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=charge",
                        "rose.ratelimit.rules[0].stage=INVOKE",
                        "rose.ratelimit.rules[0].target=invoke:com.example.BillingService#charge",
                        "rose.ratelimit.rules[0].kind=composite",
                        "rose.ratelimit.rules[0].behaviors=rate-limit,bulkhead",
                        "rose.ratelimit.rules[0].bulkhead.max-concurrent-calls=1",
                        "rose.ratelimit.rules[0].bulkhead.max-wait-duration=0ms",
                        "rose.ratelimit.rules[0].capacity=100",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    RoseResilience resilience = context.getBean(RoseResilience.class);
                    CountDownLatch entered = new CountDownLatch(1);
                    CountDownLatch release = new CountDownLatch(1);
                    AtomicReference<Throwable> rejected = new AtomicReference<>();
                    Thread holder = new Thread(() -> {
                        try {
                            resilience.call("charge", () -> {
                                entered.countDown();
                                release.await(5, TimeUnit.SECONDS);
                                return "held";
                            });
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    holder.start();
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    Thread challenger = new Thread(() -> {
                        try {
                            resilience.call("charge", () -> "overflow");
                        } catch (Throwable t) {
                            rejected.set(t);
                        }
                    });
                    challenger.start();
                    challenger.join(5000);
                    release.countDown();
                    holder.join(5000);
                    assertThat(rejected.get()).isInstanceOf(BulkheadFullException.class);
                });
    }
}
