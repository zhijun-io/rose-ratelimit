package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties.Rule;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

/**
 * Validates rate limit properties immediately after binding, providing early feedback
 * before bean creation. Inspired by microsphere's ConfigurationPropertiesBindingListener.
 */
public class RateLimitPropertiesBindingInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPropertiesBindingInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Binder binder = Binder.get(context.getEnvironment());
        RateLimitProperties properties =
                binder.bind("rose.ratelimit", RateLimitProperties.class).orElse(null);

        if (properties == null) {
            return;
        }

        if (!properties.isEnabled()) {
            log.info("Rate limiting is disabled (rose.ratelimit.enabled=false)");
            return;
        }

        validateProperties(properties);
        logConfiguration(properties);
    }

    private void validateProperties(RateLimitProperties properties) {
        List<Rule> rules = properties.getRules();
        if (rules.isEmpty()) {
            log.warn("No rate limit rules configured. Add rules under rose.ratelimit.rules");
            return;
        }

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule == null) {
                throw new IllegalStateException("rose.ratelimit.rules[" + i + "] must not be null");
            }
            if (rule.getId() == null || rule.getId().isBlank()) {
                throw new IllegalStateException("rose.ratelimit.rules[" + i + "].id is required");
            }
            if (!ids.add(rule.getId())) {
                throw new IllegalStateException("Duplicate rule id: " + rule.getId());
            }
        }
    }

    private void logConfiguration(RateLimitProperties properties) {
        long enabledRules =
                properties.getRules().stream().filter(Rule::isEnabled).count();

        log.info(
                "Rate limiting configured: {} enabled rules, store={}, failureMode={}",
                enabledRules,
                properties.getStore().getType(),
                properties.getFailureMode());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
