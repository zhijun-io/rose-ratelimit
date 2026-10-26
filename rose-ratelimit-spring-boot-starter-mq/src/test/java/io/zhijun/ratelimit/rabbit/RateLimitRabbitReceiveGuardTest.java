package io.zhijun.ratelimit.rabbit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.mq.RateLimitMqExecutor;
import io.zhijun.ratelimit.mq.RateLimitMqRejectedException;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import io.zhijun.ratelimit.web.RateLimitRules;
import io.zhijun.ratelimit.web.ResilienceBusinessDecorators;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;

class RateLimitRabbitReceiveGuardTest {
    @Test
    void secondMessageIsRejectedWhenCapacityIsOne() {
        RateLimitStore store = new RateLimitStore() {
            private int used;

            @Override
            public String name() {
                return "local";
            }

            @Override
            public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                if (used++ == 0) {
                    return RateLimitDecision.allowed(0);
                }
                return RateLimitDecision.rejected(Duration.ofSeconds(1));
            }
        };
        CompiledRule rule = compiledRule(store);
        RateLimitRules rules = new RateLimitRules("app", List.of(rule));
        rules.bindMqIndex(Map.of("rabbit:orders#g1", rule));
        @SuppressWarnings("unchecked")
        ObjectProvider<ResiliencePipelineRegistry> pipelines = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(pipelines.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<ResilienceBehaviorRegistry> behaviors = mock(ObjectProvider.class);
        when(behaviors.getIfAvailable()).thenReturn(null);
        ResilienceBusinessDecorators decorators = new ResilienceBusinessDecorators(behaviors);
        RateLimitMqExecutor executor =
                new RateLimitMqExecutor(new RateLimitGuardedInvocation(rules, List.of(), pipelines, decorators));
        RateLimitProperties properties = new RateLimitProperties();
        RabbitMqListenerRegistry listenerRegistry = new RabbitMqListenerRegistry();
        listenerRegistry.register("orders", "g1");
        RateLimitRabbitReceiveGuard guard =
                new RateLimitRabbitReceiveGuard(rules, executor, properties, listenerRegistry);
        Message message = message("orders");

        assertThat(guard.postProcessMessage(message)).isSameAs(message);
        assertThatThrownBy(() -> guard.postProcessMessage(message)).isInstanceOf(RateLimitMqRejectedException.class);
    }

    private static Message message(String queue) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(queue);
        return new Message(new byte[0], properties);
    }

    private static CompiledRule compiledRule(RateLimitStore store) {
        RateLimitKeyResolver resolver = new RateLimitKeyResolver() {
            @Override
            public String name() {
                return "mq";
            }

            @Override
            public String resolve(jakarta.servlet.http.HttpServletRequest request, String headerName) {
                return io.zhijun.ratelimit.mq.MqRateLimitContext.currentSelector();
            }
        };
        return new CompiledRule(
                "mq",
                true,
                Stage.MQ,
                null,
                java.util.Set.of(),
                "rabbit:orders#g1",
                Scope.HANDLER,
                new RateLimitPolicy(1, 1, Duration.ofDays(1)),
                store,
                resolver,
                null,
                MissingKey.REJECT,
                FailureMode.OPEN,
                429,
                "application/problem+json",
                "{\"status\":429}",
                ResilienceRuleKind.RATE_LIMIT,
                null,
                503,
                null,
                null,
                null);
    }
}
