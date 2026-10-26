package io.zhijun.ratelimit.kafka;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.lang.reflect.Method;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.util.ReflectionUtils;

/** Binds MQ rules to Kafka listener selectors at startup. */
final class RateLimitKafkaBinder {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitKafkaBinder.class);

    private RateLimitKafkaBinder() {}

    static void bind(RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        Environment environment = context.getEnvironment();
        Map<String, CompiledRule> index = new HashMap<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(beanName, false);
            if (type == null) {
                continue;
            }
            Object bean = context.getBean(beanName);
            Class<?> userClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(userClass)) {
                KafkaListener listener = AnnotatedElementUtils.findMergedAnnotation(method, KafkaListener.class);
                if (listener == null) {
                    continue;
                }
                for (String selector : KafkaListenerSelectors.selectors(listener, environment)) {
                    CompiledRule hit = rules.selectMq(selector);
                    if (hit != null) {
                        index.merge(selector, hit, rules::earlierInConfigOrder);
                    }
                }
            }
        }
        rules.bindMqIndex(index);
        validateRuleCoverage(rules, index, properties.getMq().isRequireListenerMatch());
    }

    private static void validateRuleCoverage(
            RateLimitRules rules, Map<String, CompiledRule> index, boolean requireListenerMatch) {
        Set<String> boundRuleIds = new HashSet<>();
        index.values().forEach(rule -> boundRuleIds.add(rule.id()));
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || rule.stage() != Stage.MQ) continue;
            if (rule.handler() == null || !rule.handler().startsWith("kafka:")) continue;
            if (boundRuleIds.contains(rule.id())) continue;
            if (requireListenerMatch) {
                throw new IllegalArgumentException(
                        "MQ rule '" + rule.id() + "' did not match any @KafkaListener endpoint");
            }
            LOG.warn("MQ rule '{}' did not match any @KafkaListener endpoint", rule.id());
        }
    }
}
