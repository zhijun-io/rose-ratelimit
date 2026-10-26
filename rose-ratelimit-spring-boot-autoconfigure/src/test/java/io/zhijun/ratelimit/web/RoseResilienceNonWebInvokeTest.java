package io.zhijun.ratelimit.web;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RoseResilienceNonWebInvokeTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class, RateLimitSharedAutoConfiguration.class));

    @Test
    void invokeWorksWithoutServletWebApplication() {
        AtomicInteger runs = new AtomicInteger();
        runner.withPropertyValues(
                        "spring.main.web-application-type=none",
                        "spring.application.name=batch",
                        "rose.ratelimit.rules[0].id=charge",
                        "rose.ratelimit.rules[0].stage=INVOKE",
                        "rose.ratelimit.rules[0].target=invoke:com.example.BillingService#charge",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    assertThat(context).hasSingleBean(RoseResilience.class);
                    RoseResilience resilience = context.getBean(RoseResilience.class);
                    resilience.run("charge", runs::incrementAndGet);
                    assertThat(runs).hasValue(1);
                    assertThatThrownBy(() -> resilience.run("charge", runs::incrementAndGet))
                            .isInstanceOf(RateLimitInvokeRejectedException.class);
                });
    }
}
