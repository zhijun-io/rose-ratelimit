package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import jakarta.servlet.http.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public final class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitRules rules;
    private final RateLimitExecutor executor;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimitRules rules, RateLimitExecutor executor, RateLimitProperties properties) {
        this.rules = rules;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;
        String selector =
                method.getBeanType().getName() + "#" + method.getMethod().getName();
        String bucketScope =
                method.getBeanType().getName() + "#" + method.getMethod().toGenericString();
        CompiledRule external = resolveExternalMvcRule(request, selector);
        if (!executor.execute(request, response, external, bucketScope)) return false;
        RateLimit annotation = annotation(method);
        return annotation == null
                || executor.execute(request, response, rules.annotationRule(annotation.value()), bucketScope);
    }

    private CompiledRule resolveExternalMvcRule(HttpServletRequest request, String selector) {
        if (rules.mvcIndexReady()) {
            CompiledRule bound = rules.externalMvcRule(selector);
            if (bound != null) return bound;
            if (!properties.getMvc().isRequireHandlerMatch()) {
                return rules.select(request, Stage.MVC, selector);
            }
            return null;
        }
        return rules.select(request, Stage.MVC, selector);
    }

    private RateLimit annotation(HandlerMethod method) {
        RateLimit onMethod = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RateLimit.class);
        return onMethod != null
                ? onMethod
                : org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                        method.getBeanType(), RateLimit.class);
    }
}
