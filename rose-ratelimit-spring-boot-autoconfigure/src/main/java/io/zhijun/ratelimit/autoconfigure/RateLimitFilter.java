package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

final class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitRules rules;
    private final RateLimitExecutor executor;

    RateLimitFilter(RateLimitRules rules, RateLimitExecutor executor) {
        this.rules = rules;
        this.executor = executor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (executor.execute(request, response, rules.select(request, RateLimitOptions.Stage.FILTER, null), null)) {
            chain.doFilter(request, response);
        }
    }
}
