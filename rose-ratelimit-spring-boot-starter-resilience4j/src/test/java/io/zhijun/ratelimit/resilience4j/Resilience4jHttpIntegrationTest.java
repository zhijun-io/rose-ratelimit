package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitMvcAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.resilience4j.autoconfigure.Resilience4jRateLimitAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class Resilience4jHttpIntegrationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitMvcAutoConfiguration.class,
                    Resilience4jRateLimitAutoConfiguration.class));

    @Test
    void circuitBreakerRuleRegistersPipeline() {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=cb",
                        "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].path=/api/cb/**",
                        "rose.ratelimit.rules[0].kind=circuit-breaker",
                        "rose.ratelimit.rules[0].capacity=100")
                .run(context -> assertThat(context.getBean(ResiliencePipelineRegistry.class)
                                .pipelineFor("cb"))
                        .isPresent());
    }

    @Test
    void tMvcCircuitOpen03_backendFailuresOpenCircuitAndReturn503() throws Exception {
        runner.withUserConfiguration(Mvc.class)
                .withBean("broken", RateLimitStore.class, () -> new RateLimitStore() {
                    public String name() {
                        return "broken";
                    }

                    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                        throw new RateLimitBackendException("down");
                    }
                })
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=cb",
                        "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].path=/api/cb/**",
                        "rose.ratelimit.rules[0].kind=circuit-breaker",
                        "rose.ratelimit.rules[0].store=broken",
                        "rose.ratelimit.rules[0].failure-mode=CLOSED",
                        "rose.ratelimit.rules[0].circuit-breaker.minimum-number-of-calls=2",
                        "rose.ratelimit.rules[0].circuit-breaker.sliding-window-size=2",
                        "rose.ratelimit.rules[0].circuit-breaker.failure-rate-threshold=50")
                .run(context -> {
                    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/cb")).andExpect(status().isServiceUnavailable());
                    mvc.perform(get("/api/cb")).andExpect(status().isServiceUnavailable());
                    mvc.perform(get("/api/cb")).andExpect(status().isServiceUnavailable());
                });
    }

    @Test
    void compositeRuleServesTrafficWhenCircuitClosed() throws Exception {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=cb",
                        "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].path=/api/cb/**",
                        "rose.ratelimit.rules[0].kind=composite",
                        "rose.ratelimit.rules[0].behaviors=circuit-breaker,rate-limit",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/cb")).andExpect(status().isOk());
                    mvc.perform(get("/api/cb")).andExpect(status().isTooManyRequests());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Mvc {
        @Bean
        CbController controller() {
            return new CbController();
        }
    }

    @RestController
    static class CbController {
        @GetMapping("/api/cb")
        String cb() {
            return "ok";
        }
    }
}
