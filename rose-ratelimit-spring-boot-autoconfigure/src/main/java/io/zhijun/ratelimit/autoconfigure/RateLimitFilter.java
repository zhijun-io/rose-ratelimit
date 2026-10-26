package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

final class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitRules rules;
    private final RateLimitExecutor executor;
    private final ResilienceBusinessDecorators businessDecorators;

    RateLimitFilter(RateLimitRules rules, RateLimitExecutor executor, ResilienceBusinessDecorators businessDecorators) {
        this.rules = rules;
        this.executor = executor;
        this.businessDecorators = businessDecorators;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CompiledRule rule = rules.select(request, RateLimitOptions.Stage.FILTER, null);
        if (executor.execute(request, response, rule, null)) {
            ResilienceHttpFilterChain.doFilter(businessDecorators, rule, chain, request, response);
        }
    }
}
