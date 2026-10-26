package io.zhijun.ratelimit.resilience4j;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.resilience4j.autoconfigure.Resilience4jRateLimitAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RateLimitMvcBusinessDecorationWarnTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    Resilience4jRateLimitAutoConfiguration.class));

    @Test
    void mvcRetryBehaviorLogsStartupWarning(CapturedOutput output) {
        runner.withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=mvc-retry",
                        "rose.ratelimit.rules[0].stage=MVC",
                        "rose.ratelimit.rules[0].path=/api/**",
                        "rose.ratelimit.rules[0].kind=composite",
                        "rose.ratelimit.rules[0].behaviors=rate-limit,retry",
                        "rose.ratelimit.rules[0].capacity=10")
                .run(context -> assertThat(context).hasNotFailed());
        assertThat(output.getOut() + output.getErr())
                .contains("Rule 'mvc-retry'")
                .contains("stage MVC does not decorate");
    }
}
