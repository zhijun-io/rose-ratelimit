package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.server.PathContainer;
import org.springframework.util.PatternMatchUtils;

/** Compiles all references and matchers before traffic is accepted. */
public final class RateLimitRules {
    private final List<CompiledRule> rules;
    private final Map<String, CompiledRule> byId;
    private final String namespace;

    public RateLimitRules(String namespace, List<CompiledRule> rules) {
        this.namespace = namespace;
        this.rules = List.copyOf(rules);
        Map<String, CompiledRule> ids = new LinkedHashMap<>();
        for (CompiledRule rule : rules) {
            if (ids.putIfAbsent(rule.id(), rule) != null)
                throw new IllegalArgumentException("Duplicate rule: " + rule.id());
        }
        byId = Map.copyOf(ids);
    }

    public String namespace() {
        return namespace;
    }

    public CompiledRule annotationRule(String id) {
        CompiledRule rule = byId.get(id);
        if (rule == null || rule.stage() != Stage.MVC) {
            throw new IllegalArgumentException("@RateLimit must reference an existing MVC rule: " + id);
        }
        return rule;
    }

    public CompiledRule select(HttpServletRequest request, Stage stage, String handler) {
        PathContainer path = PathContainer.parsePath(
                request.getRequestURI().substring(request.getContextPath().length()));
        for (CompiledRule rule : rules) {
            if (!rule.enabled() || rule.stage() != stage || (rule.path() == null && rule.handler() == null)) continue;
            if (rule.path() != null && !rule.path().matches(path)) continue;
            if (!rule.methods().isEmpty() && !rule.methods().contains(request.getMethod())) continue;
            if (rule.handler() != null && (handler == null || !PatternMatchUtils.simpleMatch(rule.handler(), handler)))
                continue;
            return rule;
        }
        return null;
    }
}
