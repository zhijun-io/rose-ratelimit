package io.zhijun.ratelimit.web;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class RoseResilienceInvokeTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class));

    @Test
    void invokeRuleLimitsExplicitCalls() {
        AtomicInteger runs = new AtomicInteger();
        runner.withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=charge",
                        "rose.ratelimit.rules[0].stage=INVOKE",
                        "rose.ratelimit.rules[0].target=invoke:com.example.BillingService#charge",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    RoseResilience resilience = context.getBean(RoseResilience.class);
                    resilience.run("charge", runs::incrementAndGet);
                    assertThat(runs).hasValue(1);
                    assertThatThrownBy(() -> resilience.run("charge", runs::incrementAndGet))
                            .isInstanceOf(RateLimitInvokeRejectedException.class);
                    assertThat(runs).hasValue(1);
                });
    }

    @Test
    void invokeSelectorMustMatchPatternWhenProvided() {
        runner.withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=charge",
                        "rose.ratelimit.rules[0].stage=INVOKE",
                        "rose.ratelimit.rules[0].target=invoke:*BillingService#charge",
                        "rose.ratelimit.rules[0].capacity=10")
                .run(context -> {
                    RoseResilience resilience = context.getBean(RoseResilience.class);
                    assertThat(resilience.call("charge", "com.example.BillingService#charge", () -> "ok"))
                            .isEqualTo("ok");
                    assertThatThrownBy(() -> resilience.call("charge", "com.example.Other#charge", () -> "x"))
                            .isInstanceOf(IllegalArgumentException.class);
                });
    }
}
