package io.zhijun.ratelimit.rabbit.autoconfigure;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.mq.RateLimitMqExecutor;
import io.zhijun.ratelimit.mq.autoconfigure.RateLimitMqAutoConfiguration;
import io.zhijun.ratelimit.rabbit.RabbitMqListenerRegistry;
import io.zhijun.ratelimit.rabbit.RateLimitRabbitBindingInitializer;
import io.zhijun.ratelimit.rabbit.RateLimitRabbitReceiveGuard;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RateLimitMqAutoConfiguration.class)
@ConditionalOnClass(RabbitListener.class)
@ConditionalOnProperty(prefix = "rose.ratelimit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitRabbitAutoConfiguration {
    @Bean
    RabbitMqListenerRegistry rabbitMqListenerRegistry() {
        return new RabbitMqListenerRegistry();
    }

    @Bean
    @ConditionalOnBean(RateLimitRules.class)
    RateLimitRabbitBindingInitializer rateLimitRabbitBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        return new RateLimitRabbitBindingInitializer(rules, context, properties);
    }

    @Bean
    @ConditionalOnBean(RateLimitMqExecutor.class)
    RateLimitRabbitReceiveGuard rateLimitRabbitReceiveGuard(
            RateLimitRules rules,
            RateLimitMqExecutor executor,
            RateLimitProperties properties,
            RabbitMqListenerRegistry listenerRegistry) {
        return new RateLimitRabbitReceiveGuard(rules, executor, properties, listenerRegistry);
    }
}
