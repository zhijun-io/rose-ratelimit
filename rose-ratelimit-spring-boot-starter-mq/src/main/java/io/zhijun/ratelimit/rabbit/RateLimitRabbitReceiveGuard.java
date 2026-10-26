package io.zhijun.ratelimit.rabbit;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.mq.RateLimitMqExecutor;
import io.zhijun.ratelimit.mq.RateLimitMqRejectedException;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

/** Rate-limits Rabbit messages before they reach {@link RabbitListener} methods. */
public final class RateLimitRabbitReceiveGuard implements MessagePostProcessor {
    private final RateLimitRules rules;
    private final RateLimitMqExecutor executor;
    private final RateLimitProperties properties;
    private final RabbitMqListenerRegistry listenerRegistry;

    public RateLimitRabbitReceiveGuard(
            RateLimitRules rules,
            RateLimitMqExecutor executor,
            RateLimitProperties properties,
            RabbitMqListenerRegistry listenerRegistry) {
        this.rules = rules;
        this.executor = executor;
        this.properties = properties;
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    public Message postProcessMessage(Message message) {
        String selector =
                listenerRegistry.selectorForQueue(message.getMessageProperties().getConsumerQueue());
        if (selector == null) {
            return message;
        }
        CompiledRule rule = rules.externalMqRule(selector);
        if (rule == null && rules.mqIndexReady() && !properties.getMq().isRequireListenerMatch()) {
            rule = rules.selectMq(selector);
        }
        try {
            return executor.invokeGuarded(rule, selector, () -> message);
        } catch (RateLimitMqRejectedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
