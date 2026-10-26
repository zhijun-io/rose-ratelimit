package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.resilience4j.autoconfigure.Resilience4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.web.RoseResilience;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class RoseResilienceInvokeTimeLimiterTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    Resilience4jRateLimitAutoConfiguration.class));

    @Test
    void compositeTimeLimiterAppliesAfterRateLimitAllow() {
        runner.withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=charge",
                        "rose.ratelimit.rules[0].stage=INVOKE",
                        "rose.ratelimit.rules[0].target=invoke:com.example.BillingService#charge",
                        "rose.ratelimit.rules[0].kind=composite",
                        "rose.ratelimit.rules[0].behaviors=rate-limit,time-limiter",
                        "rose.ratelimit.rules[0].time-limiter.timeout-duration=50ms",
                        "rose.ratelimit.rules[0].capacity=100",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    RoseResilience resilience = context.getBean(RoseResilience.class);
                    assertThatThrownBy(() -> resilience.call("charge", () -> {
                                Thread.sleep(500);
                                return "late";
                            }))
                            .hasRootCauseInstanceOf(TimeoutException.class);
                });
    }
}
