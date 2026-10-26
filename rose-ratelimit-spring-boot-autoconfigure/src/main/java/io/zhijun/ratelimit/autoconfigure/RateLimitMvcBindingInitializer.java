package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.RateLimit;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Runs MVC index binding after handler mappings are registered. */
final class RateLimitMvcBindingInitializer implements SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final RateLimitProperties properties;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    RateLimitMvcBindingInitializer(
            RateLimitRules rules,
            RateLimitProperties properties,
            ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.rules = rules;
        this.properties = properties;
        this.mappings = mappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        RateLimitMvcBinder.bind(rules, mappings, properties);
        mappings.orderedStream()
                .forEach(mapping -> mapping.getHandlerMethods().values().forEach(this::warmAnnotationRule));
    }

    private void warmAnnotationRule(HandlerMethod method) {
        RateLimit annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RateLimit.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RateLimit.class);
        }
        if (annotation != null) {
            rules.annotationRule(annotation.value());
        }
    }
}
