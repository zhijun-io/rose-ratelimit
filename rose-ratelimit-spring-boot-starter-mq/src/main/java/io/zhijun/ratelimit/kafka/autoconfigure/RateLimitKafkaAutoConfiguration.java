package io.zhijun.ratelimit.kafka.autoconfigure;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.kafka.*;
import io.zhijun.ratelimit.mq.RateLimitMqExecutor;
import io.zhijun.ratelimit.mq.autoconfigure.RateLimitMqAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;

@AutoConfiguration(after = RateLimitMqAutoConfiguration.class)
@ConditionalOnClass(KafkaListener.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitKafkaAutoConfiguration {
    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitKafkaBindingInitializer rateLimitKafkaBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        return new RateLimitKafkaBindingInitializer(rules, context, properties);
    }

    @Bean
    @ConditionalOnBean(RateLimitMqExecutor.class)
    RateLimitKafkaRecordInterceptor rateLimitKafkaRecordInterceptor(
            RateLimitRules rules, RateLimitMqExecutor executor, RateLimitProperties properties) {
        return new RateLimitKafkaRecordInterceptor(rules, executor, properties);
    }

    @Bean
    @ConditionalOnBean(RateLimitKafkaRecordInterceptor.class)
    static RateLimitKafkaListenerFactoryRegistrar rateLimitKafkaListenerFactoryRegistrar(
            RateLimitKafkaRecordInterceptor interceptor) {
        return new RateLimitKafkaListenerFactoryRegistrar(interceptor);
    }
}
