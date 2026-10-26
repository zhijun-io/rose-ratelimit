package io.zhijun.ratelimit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class RateLimitPropertiesBindingInitializerTest {
    @Test
    void ignoresContextsWithoutRateLimitConfiguration() {
        initialize(Map.of());
    }

    @Test
    void disabledFeatureDoesNotValidateRules() {
        initialize(Map.of("rose.ratelimit.enabled", "false", "rose.ratelimit.rules[0].id", ""));
    }

    @Test
    void validatesRequiredAndUniqueRuleIds() {
        assertThatThrownBy(() -> initialize(Map.of("rose.ratelimit.rules[0].id", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rules[0].id is required");
        assertThatThrownBy(() -> initialize(Map.of(
                        "rose.ratelimit.rules[0].id", "same",
                        "rose.ratelimit.rules[1].id", "same")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate rule id: same");
    }

    @Test
    void acceptsConfiguredFilterAndMvcRules() {
        initialize(Map.of(
                "rose.ratelimit.rules[0].id", "filter",
                "rose.ratelimit.rules[0].stage", "FILTER",
                "rose.ratelimit.rules[1].id", "mvc",
                "rose.ratelimit.rules[1].stage", "MVC"));
    }

    private static void initialize(Map<String, Object> properties) {
        try (var context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            new RateLimitPropertiesBindingInitializer().initialize(context);
        }
    }
}
