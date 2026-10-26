package io.zhijun.ratelimit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Applies {@link ResilienceBusinessDecorators} around {@link FilterChain#doFilter} after rate-limit allow. */
public final class ResilienceHttpFilterChain {
    private ResilienceHttpFilterChain() {}

    public static void doFilter(
            ResilienceBusinessDecorators decorators,
            CompiledRule rule,
            FilterChain chain,
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {
        if (rule == null || !rule.usesBusinessDecoration()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            decorators.run(rule, () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
