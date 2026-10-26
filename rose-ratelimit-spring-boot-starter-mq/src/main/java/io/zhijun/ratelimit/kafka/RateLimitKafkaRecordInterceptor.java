package io.zhijun.ratelimit.kafka;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.mq.RateLimitMqExecutor;
import io.zhijun.ratelimit.mq.RateLimitMqRejectedException;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

/** Rate-limits Kafka records before they reach {@link KafkaListener} methods. */
public final class RateLimitKafkaRecordInterceptor implements RecordInterceptor<Object, Object> {
    private final RateLimitRules rules;
    private final RateLimitMqExecutor executor;
    private final RateLimitProperties properties;

    public RateLimitKafkaRecordInterceptor(
            RateLimitRules rules, RateLimitMqExecutor executor, RateLimitProperties properties) {
        this.rules = rules;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(
            ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        String groupId = consumer.groupMetadata().groupId();
        String selector = "kafka:" + record.topic() + "#" + groupId;
        CompiledRule rule = rules.externalMqRule(selector);
        if (rule == null && rules.mqIndexReady() && !properties.getMq().isRequireListenerMatch()) {
            rule = rules.selectMq(selector);
        }
        try {
            return executor.invokeGuarded(rule, selector, () -> record);
        } catch (RateLimitMqRejectedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
