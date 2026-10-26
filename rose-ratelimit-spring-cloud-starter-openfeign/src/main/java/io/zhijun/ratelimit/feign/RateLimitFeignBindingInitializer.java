package io.zhijun.ratelimit.feign;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

public final class RateLimitFeignBindingInitializer implements SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final ApplicationContext context;
    private final RateLimitProperties properties;

    public RateLimitFeignBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        this.rules = rules;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getFeign().isEnabled()) {
            return;
        }
        RateLimitFeignBinder.bind(rules, context, properties);
    }
}
