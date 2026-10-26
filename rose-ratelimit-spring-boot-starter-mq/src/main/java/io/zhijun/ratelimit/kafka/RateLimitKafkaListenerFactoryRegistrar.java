package io.zhijun.ratelimit.kafka;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

/** Registers {@link RateLimitKafkaRecordInterceptor} on Kafka listener container factories. */
public final class RateLimitKafkaListenerFactoryRegistrar implements BeanPostProcessor {
    private final RateLimitKafkaRecordInterceptor interceptor;

    public RateLimitKafkaListenerFactoryRegistrar(RateLimitKafkaRecordInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            org.springframework.kafka.listener.RecordInterceptor raw = interceptor;
            factory.setRecordInterceptor(raw);
        }
        return bean;
    }
}
