package io.zhijun.ratelimit.resilience4j;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Smoke: FILTER + bulkhead wiring through {@link io.zhijun.ratelimit.autoconfigure.RateLimitFilter}. */
class RateLimitFilterBulkheadHttpTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitMvcAutoConfiguration.class,
                    Resilience4jRateLimitAutoConfiguration.class));

    @Test
    void filterStageBulkheadAllowsRequestWhenNotFull() throws Exception {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.rules[0].id=api",
                        "rose.ratelimit.rules[0].stage=FILTER",
                        "rose.ratelimit.rules[0].path=/api/hold",
                        "rose.ratelimit.rules[0].kind=composite",
                        "rose.ratelimit.rules[0].behaviors=rate-limit,bulkhead",
                        "rose.ratelimit.rules[0].bulkhead.max-concurrent-calls=2",
                        "rose.ratelimit.rules[0].capacity=100",
                        "rose.ratelimit.rules[0].refill-period=1s")
                .run(context -> {
                    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/hold")).andExpect(status().isOk());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Mvc {
        @Bean
        HoldController holdController() {
            return new HoldController();
        }
    }

    @RestController
    static class HoldController {
        @GetMapping("/api/hold")
        String hold() {
            return "ok";
        }
    }
}
