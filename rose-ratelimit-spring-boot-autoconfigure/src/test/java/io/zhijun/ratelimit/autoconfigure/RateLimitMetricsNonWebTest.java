package io.zhijun.ratelimit.autoconfigure;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.web.RoseResilience;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RateLimitMetricsNonWebTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MeterRegistryConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitMetricsAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class));

    @Test
    void invokeRecordsMicrometerCounterWithoutServletWeb() {
        runner.withPropertyValues(
                        "spring.main.web-application-type=none",
                        "spring.application.name=batch",
                        "rose.ratelimit.rules[0].id=charge",
                        "rose.ratelimit.rules[0].stage=INVOKE",
                        "rose.ratelimit.rules[0].target=invoke:com.example.Service#run",
                        "rose.ratelimit.rules[0].capacity=10",
                        "rose.ratelimit.rules[0].refill-period=1s")
                .run(context -> {
                    context.getBean(RoseResilience.class).run("charge", () -> {});
                    SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
                    assertThat(registry.find("rose.ratelimit.requests")
                                    .tag("rule", "charge")
                                    .tag("result", "allowed")
                                    .counter())
                            .isNotNull()
                            .extracting(counter -> counter.count())
                            .isEqualTo(1.0);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
