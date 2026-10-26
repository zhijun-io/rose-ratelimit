package io.zhijun.ratelimit.kafka;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

public final class RateLimitKafkaBindingInitializer implements SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final ApplicationContext context;
    private final RateLimitProperties properties;

    public RateLimitKafkaBindingInitializer(
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
        RateLimitKafkaBinder.bind(rules, context, properties);
        if (!context.getBeansOfType(RateLimitKafkaRecordInterceptor.class).isEmpty()) {
            RateLimitKafkaRecordInterceptor interceptor = context.getBean(RateLimitKafkaRecordInterceptor.class);
            for (ConcurrentKafkaListenerContainerFactory<?, ?> factory : context.getBeansOfType(
                            ConcurrentKafkaListenerContainerFactory.class)
                    .values()) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                org.springframework.kafka.listener.RecordInterceptor raw = interceptor;
                factory.setRecordInterceptor(raw);
            }
        }
    }
}
