package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;

final class DefaultRateLimitKeyResolver implements RateLimitKeyResolver {
    private final String name;

    DefaultRateLimitKeyResolver(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void validate(String header) {
        if (name.equals("header") && (header == null || !header.matches("[!#$%&'*+.^_`|~0-9a-zA-Z-]+"))) {
            throw new IllegalArgumentException("key-header must be a valid HTTP header name");
        }
    }

    @Override
    public String resolve(HttpServletRequest request, String header) {
        return switch (name) {
            case "ip" -> request.getRemoteAddr();
            case "principal" ->
                request.getUserPrincipal() == null
                        ? null
                        : request.getUserPrincipal().getName();
            case "header" -> request.getHeader(header);
            default -> throw new IllegalStateException("Unknown built-in resolver");
        };
    }
}
