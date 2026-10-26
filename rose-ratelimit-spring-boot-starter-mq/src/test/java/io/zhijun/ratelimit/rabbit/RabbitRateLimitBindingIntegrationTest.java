package io.zhijun.ratelimit.rabbit;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.mq.autoconfigure.RateLimitMqAutoConfiguration;
import io.zhijun.ratelimit.rabbit.autoconfigure.RateLimitRabbitAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class RabbitRateLimitBindingIntegrationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitMqAutoConfiguration.class,
                    RateLimitRabbitAutoConfiguration.class));

    @Test
    void rabbitListenerBindsMqRuleAtStartup() {
        String selector = "rabbit:orders#g1";
        runner.withUserConfiguration(ListenerApp.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=orders",
                        "rose.ratelimit.rules[0].stage=MQ",
                        "rose.ratelimit.rules[0].target=mq:rabbit:orders#g1",
                        "rose.ratelimit.rules[0].capacity=10",
                        "rose.ratelimit.rules[0].refill-period=1s")
                .run(context -> {
                    assertThat(context.getBean(RateLimitRules.class).mqIndexReady())
                            .isTrue();
                    assertThat(context.getBean(RateLimitRules.class).externalMqRule(selector))
                            .isNotNull();
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class ListenerApp {
        @Bean
        OrderListener orderListener() {
            return new OrderListener();
        }
    }

    static class OrderListener {
        @RabbitListener(queues = "orders", id = "g1")
        void onOrder(String payload) {}
    }
}
