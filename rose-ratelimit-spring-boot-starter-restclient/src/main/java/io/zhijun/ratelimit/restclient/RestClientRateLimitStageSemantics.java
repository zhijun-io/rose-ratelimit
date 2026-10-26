package io.zhijun.ratelimit.restclient;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import io.zhijun.ratelimit.web.RateLimitStageSemantics;
import io.zhijun.ratelimit.web.StageGuardDecision;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

enum RestClientRateLimitStageSemantics implements RateLimitStageSemantics {
    INSTANCE;

    @Override
    public String resolveIdentity(CompiledRule rule, String selector) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
        RateLimitKeyResolver resolver = rule.resolver();
        if (request == null && isBuiltInResolver(resolver.name())) {
            return null;
        }
        return resolver.resolve(request, rule.keyHeader());
    }

    @Override
    public Exception reject(StageGuardDecision decision, CompiledRule rule) {
        return new RateLimitRestClientRejectedException(
                decision.status(), decision.body(), decision.result(), decision.retryAfter());
    }

    private static boolean isBuiltInResolver(String name) {
        return name.equals("ip") || name.equals("principal") || name.equals("header");
    }
}
