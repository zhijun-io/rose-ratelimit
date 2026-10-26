package io.zhijun.ratelimit.rabbit;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

public final class RateLimitRabbitBindingInitializer implements SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final ApplicationContext context;
    private final RateLimitProperties properties;

    public RateLimitRabbitBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        this.rules = rules;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getMq().isEnabled()) {
            return;
        }
        RabbitMqListenerRegistry listenerRegistry = context.getBean(RabbitMqListenerRegistry.class);
        RateLimitRabbitBinder.bind(rules, context, properties, listenerRegistry);
        if (context.getBeansOfType(RateLimitRabbitReceiveGuard.class).isEmpty()) {
            return;
        }
        RateLimitRabbitReceiveGuard guard = context.getBean(RateLimitRabbitReceiveGuard.class);
        for (SimpleRabbitListenerContainerFactory factory : context.getBeansOfType(
                        SimpleRabbitListenerContainerFactory.class)
                .values()) {
            factory.setAfterReceivePostProcessors(guard);
        }
    }
}
