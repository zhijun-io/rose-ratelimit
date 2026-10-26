package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.web.RateLimitOptions.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.server.PathContainer;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.util.ServletRequestPathUtils;

/** Compiles all references and matchers before traffic is accepted. */
public final class RateLimitRules {
    private final List<CompiledRule> rules;
    private final Map<String, CompiledRule> byId;
    private final String namespace;
    private Map<String, CompiledRule> mvcExternalBySelector = Map.of();
    private boolean mvcIndexReady;
    private Map<String, CompiledRule> feignExternalBySelector = Map.of();
    private boolean feignIndexReady;
    private Map<String, CompiledRule> mybatisExternalByStatementId = Map.of();
    private boolean mybatisIndexReady;
    private Map<String, CompiledRule> mqExternalBySelector = Map.of();
    private boolean mqIndexReady;

    public RateLimitRules(String namespace, List<CompiledRule> rules) {
        this.namespace = namespace;
        this.rules = List.copyOf(rules);
        Map<String, CompiledRule> ids = new HashMap<>();
        for (CompiledRule rule : this.rules) {
            if (ids.putIfAbsent(rule.id(), rule) != null) {
                throw new IllegalArgumentException("Duplicate rule: " + rule.id());
            }
        }
        byId = Map.copyOf(ids);
    }

    public String namespace() {
        return namespace;
    }

    public List<CompiledRule> rules() {
        return rules;
    }

    public CompiledRule annotationRule(String id) {
        CompiledRule rule = byId.get(id);
        if (rule == null || rule.stage() != Stage.MVC) {
            throw new IllegalArgumentException("@RateLimit must reference an existing MVC rule: " + id);
        }
        return rule;
    }

    public static PathContainer applicationPath(HttpServletRequest request) {
        String path =
                ServletRequestPathUtils.parse(request).pathWithinApplication().value();
        return PathContainer.parsePath(path);
    }

    public void bindMvcIndex(Map<String, CompiledRule> externalBySelector) {
        if (mvcIndexReady) {
            throw new IllegalStateException("MVC rate-limit index already bound");
        }
        mvcExternalBySelector = Map.copyOf(externalBySelector);
        mvcIndexReady = true;
    }

    public boolean mvcIndexReady() {
        return mvcIndexReady;
    }

    public CompiledRule externalMvcRule(String selector) {
        return mvcExternalBySelector.get(selector);
    }

    public void bindFeignIndex(Map<String, CompiledRule> externalBySelector) {
        if (feignIndexReady) {
            throw new IllegalStateException("Feign rate-limit index already bound");
        }
        feignExternalBySelector = Map.copyOf(externalBySelector);
        feignIndexReady = true;
    }

    public boolean feignIndexReady() {
        return feignIndexReady;
    }

    public CompiledRule externalFeignRule(String selector) {
        return feignExternalBySelector.get(selector);
    }

    public CompiledRule selectFeign(String selector) {
        return selectBinding(Stage.FEIGN, selector);
    }

    public void bindMybatisIndex(Map<String, CompiledRule> externalByStatementId) {
        if (mybatisIndexReady) {
            throw new IllegalStateException("MyBatis rate-limit index already bound");
        }
        mybatisExternalByStatementId = Map.copyOf(externalByStatementId);
        mybatisIndexReady = true;
    }

    public boolean mybatisIndexReady() {
        return mybatisIndexReady;
    }

    public CompiledRule externalMybatisRule(String statementId) {
        return mybatisExternalByStatementId.get(statementId);
    }

    public CompiledRule selectMybatis(String statementId) {
        return selectBinding(Stage.MYBATIS, statementId);
    }

    public CompiledRule selectJdbc(String selector) {
        return selectBinding(Stage.JDBC, selector);
    }

    public void bindMqIndex(Map<String, CompiledRule> externalBySelector) {
        if (externalBySelector.isEmpty()) {
            mqIndexReady = true;
            return;
        }
        if (mqIndexReady) {
            Map<String, CompiledRule> merged = new LinkedHashMap<>(mqExternalBySelector);
            for (Map.Entry<String, CompiledRule> entry : externalBySelector.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), this::earlierInConfigOrder);
            }
            mqExternalBySelector = Map.copyOf(merged);
            return;
        }
        mqExternalBySelector = Map.copyOf(externalBySelector);
        mqIndexReady = true;
    }

    public boolean mqIndexReady() {
        return mqIndexReady;
    }

    public CompiledRule externalMqRule(String selector) {
        return mqExternalBySelector.get(selector);
    }

    public CompiledRule selectMq(String selector) {
        return selectBinding(Stage.MQ, selector);
    }

    public CompiledRule selectInvoke(String selector) {
        return selectBinding(Stage.INVOKE, selector);
    }

    private CompiledRule selectBinding(Stage stage, String selector) {
        for (CompiledRule rule : rules) {
            if (!rule.enabled() || rule.stage() != stage || rule.handler() == null) continue;
            if (!PatternMatchUtils.simpleMatch(rule.handler(), selector)) continue;
            return rule;
        }
        return null;
    }

    public CompiledRule rule(String id) {
        CompiledRule rule = byId.get(id);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown rate-limit rule: " + id);
        }
        return rule;
    }

    public CompiledRule select(HttpServletRequest request, Stage stage, String handler) {
        return select(applicationPath(request), request.getMethod(), stage, handler);
    }

    public CompiledRule select(PathContainer path, String httpMethod, Stage stage, String handler) {
        for (CompiledRule rule : rules) {
            if (!rule.enabled() || rule.stage() != stage || (rule.path() == null && rule.handler() == null)) continue;
            if (rule.path() != null && !rule.path().matches(path)) continue;
            if (!rule.methods().isEmpty() && !rule.methods().contains(httpMethod)) continue;
            if (rule.handler() != null && (handler == null || !PatternMatchUtils.simpleMatch(rule.handler(), handler)))
                continue;
            return rule;
        }
        return null;
    }

    int configOrder(CompiledRule rule) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id().equals(rule.id())) return i;
        }
        return Integer.MAX_VALUE;
    }

    public CompiledRule earlierInConfigOrder(CompiledRule current, CompiledRule candidate) {
        if (current == null) return candidate;
        return configOrder(candidate) < configOrder(current) ? candidate : current;
    }
}
