package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public final class RateLimitInterceptor implements HandlerInterceptor, SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final RateLimitExecutor executor;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    public RateLimitInterceptor(
            RateLimitRules rules, RateLimitExecutor executor, ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.rules = rules;
        this.executor = executor;
        this.mappings = mappings;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;
        String selector =
                method.getBeanType().getName() + "#" + method.getMethod().getName();
        String bucketScope =
                method.getBeanType().getName() + "#" + method.getMethod().toGenericString();
        if (!executor.execute(request, response, rules.select(request, Stage.MVC, selector), bucketScope)) return false;
        RateLimit annotation = annotation(method);
        return annotation == null
                || executor.execute(request, response, rules.annotationRule(annotation.value()), bucketScope);
    }

    @Override
    public void afterSingletonsInstantiated() {
        mappings.orderedStream()
                .forEach(mapping -> mapping.getHandlerMethods().values().forEach(method -> {
                    RateLimit annotation = annotation(method);
                    if (annotation != null) rules.annotationRule(annotation.value());
                }));
    }

    private RateLimit annotation(HandlerMethod method) {
        RateLimit annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RateLimit.class);
        return annotation != null
                ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RateLimit.class);
    }
}
