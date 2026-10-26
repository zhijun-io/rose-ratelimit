package io.zhijun.ratelimit.feign;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.lang.reflect.Method;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

/** Binds FEIGN rules to client method selectors at startup. */
final class RateLimitFeignBinder {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFeignBinder.class);

    private RateLimitFeignBinder() {}

    static void bind(RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        Map<String, CompiledRule> index = new HashMap<>();
        Set<Class<?>> clientTypes = new LinkedHashSet<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(beanName, false);
            if (type == null || !type.isInterface()) {
                continue;
            }
            if (AnnotatedElementUtils.hasAnnotation(type, FeignClient.class)) {
                clientTypes.add(type);
            }
        }
        for (Class<?> type : clientTypes) {
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(type)) {
                if (method.isDefault() || ReflectionUtils.isObjectMethod(method)) {
                    continue;
                }
                String selector = selector(type, method);
                CompiledRule hit = rules.selectFeign(selector);
                if (hit != null) {
                    index.merge(selector, hit, rules::earlierInConfigOrder);
                }
            }
        }
        rules.bindFeignIndex(index);
        validateRuleCoverage(rules, index, properties.getFeign().isRequireClientMatch());
    }

    private static void validateRuleCoverage(
            RateLimitRules rules, Map<String, CompiledRule> index, boolean requireClientMatch) {
        Set<String> boundRuleIds = new HashSet<>();
        index.values().forEach(rule -> boundRuleIds.add(rule.id()));
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || rule.stage() != Stage.FEIGN) continue;
            if (rule.handler() == null) continue;
            if (boundRuleIds.contains(rule.id())) continue;
            if (requireClientMatch) {
                throw new IllegalArgumentException(
                        "FEIGN rule '" + rule.id() + "' did not match any @FeignClient method");
            }
            LOG.warn("FEIGN rule '{}' did not match any @FeignClient method", rule.id());
        }
    }

    private static String selector(Class<?> type, Method method) {
        return type.getName() + "#" + method.getName();
    }
}
