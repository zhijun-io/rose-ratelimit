package io.zhijun.ratelimit.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.mq.MqRateLimitContext;
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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RateLimitKafkaRecordInterceptorTest {
    @Test
    void secondRecordIsRejectedWhenCapacityIsOne() {
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
        rules.bindMqIndex(Map.of("kafka:orders#g1", rule));
        @SuppressWarnings("unchecked")
        ObjectProvider<ResiliencePipelineRegistry> pipelines = mock(ObjectProvider.class);
        when(pipelines.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<ResilienceBehaviorRegistry> behaviors = mock(ObjectProvider.class);
        when(behaviors.getIfAvailable()).thenReturn(null);
        ResilienceBusinessDecorators decorators = new ResilienceBusinessDecorators(behaviors);
        RateLimitMqExecutor executor =
                new RateLimitMqExecutor(new RateLimitGuardedInvocation(rules, List.of(), pipelines, decorators));
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitKafkaRecordInterceptor interceptor = new RateLimitKafkaRecordInterceptor(rules, executor, properties);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("orders", 0, 0L, "k", "v");
        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);
        ConsumerGroupMetadata metadata = new ConsumerGroupMetadata("g1");
        when(consumer.groupMetadata()).thenReturn(metadata);

        assertThat(interceptor.intercept(record, consumer)).isSameAs(record);
        assertThatThrownBy(() -> interceptor.intercept(record, consumer))
                .isInstanceOf(RateLimitMqRejectedException.class);
    }

    private static CompiledRule compiledRule(RateLimitStore store) {
        RateLimitKeyResolver resolver = new RateLimitKeyResolver() {
            @Override
            public String name() {
                return "mq";
            }

            @Override
            public String resolve(jakarta.servlet.http.HttpServletRequest request, String headerName) {
                return MqRateLimitContext.currentSelector();
            }
        };
        return new CompiledRule(
                "mq",
                true,
                Stage.MQ,
                null,
                java.util.Set.of(),
                "kafka:orders#g1",
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
