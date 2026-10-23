package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties.Rule;
import io.zhijun.ratelimit.web.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.util.pattern.PathPatternParser;

/** Validates configuration and compiles immutable runtime rules at startup. */
final class RateLimitRuleCompiler {
    private RateLimitRuleCompiler() {}

    static RateLimitRules compile(
            RateLimitProperties properties,
            List<RateLimitPlugin> plugins,
            String applicationName,
            ClassLoader classLoader) {
        Map<String, RateLimitPlugin> registry = new HashMap<>();
        for (RateLimitPlugin plugin : plugins) {
            if (plugin.name() == null || !plugin.name().matches("[a-zA-Z0-9._-]+")) {
                throw new IllegalArgumentException("Invalid rate-limit plugin name");
            }
            if (registry.putIfAbsent(plugin.name(), plugin) != null) {
                throw new IllegalArgumentException("Duplicate rate-limit plugin: " + plugin.name());
            }
        }
        if (!(registry.get(properties.getStore().getType()) instanceof RateLimitStore)) {
            throw new IllegalArgumentException(
                    "Missing rate-limit store plugin '" + properties.getStore().getType()
                            + "'. For redis add rose-ratelimit-spring-boot-starter-redis and configure Lettuce.");
        }
        List<CompiledRule> compiled = new ArrayList<>();
        Map<String, CompiledRule> ids = new HashMap<>();
        for (int i = 0; i < properties.getRules().size(); i++) {
            Rule rule = properties.getRules().get(i);
            if (rule == null) throw new IllegalArgumentException("rose.ratelimit.rules[" + i + "] must not be null");
            try {
                if (rule.getId() == null || !rule.getId().matches("[a-zA-Z0-9._-]+")) {
                    throw new IllegalArgumentException(
                            "id must contain only letters, digits, dot, underscore or hyphen");
                }
                if (rule.getStage() == null || rule.getMissingKey() == null) {
                    throw new IllegalArgumentException("stage and missing-key are required");
                }
                if (rule.isEnabled()
                        && rule.getStage() == Stage.MVC
                        && properties.isMvcEnabled()
                        && !org.springframework.util.ClassUtils.isPresent(
                                "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
                                classLoader)) {
                    throw new IllegalArgumentException("MVC rules require Spring MVC on the classpath");
                }
                if (rule.getStage() == Stage.FILTER
                        && (rule.getPath() == null || rule.getPath().isBlank())) {
                    throw new IllegalArgumentException("FILTER requires path");
                }
                if (rule.getPath() != null && !rule.getPath().startsWith("/")) {
                    throw new IllegalArgumentException("path must start with /");
                }
                if (rule.getStage() == Stage.FILTER
                        && (rule.getHandler() != null || rule.getScope() == Scope.HANDLER)) {
                    throw new IllegalArgumentException("handler and HANDLER scope require MVC");
                }
                if (rule.getHandler() != null && rule.getHandler().isBlank()) {
                    throw new IllegalArgumentException("handler must not be blank");
                }
                String storeName =
                        rule.getStore() == null ? properties.getStore().getType() : rule.getStore();
                if (!(registry.get(storeName) instanceof RateLimitStore store)) {
                    throw new IllegalArgumentException("Missing store plugin: " + storeName);
                }
                if (!(registry.get(rule.getKeySource()) instanceof RateLimitKeyResolver resolver)) {
                    throw new IllegalArgumentException("Missing key resolver plugin: " + rule.getKeySource());
                }
                resolver.validate(rule.getKeyHeader());
                Set<String> methods = new LinkedHashSet<>();
                for (String method : rule.getMethods()) {
                    if (method == null || !method.matches("[A-Za-z]+"))
                        throw new IllegalArgumentException("Invalid HTTP method");
                    methods.add(method.toUpperCase(Locale.ROOT));
                }
                if (rule.getStatus() < 400 || rule.getStatus() > 599) {
                    throw new IllegalArgumentException("status must be an HTTP error status");
                }
                MediaType responseType = MediaType.parseMediaType(rule.getContentType());
                if (!responseType.isConcrete()) throw new IllegalArgumentException("content-type must be concrete");
                if (rule.getContentType().contains("\r")
                        || rule.getContentType().contains("\n")) {
                    throw new IllegalArgumentException("Invalid content-type");
                }
                String body = rule.getBody() == null
                        ? "{\"type\":\"about:blank\",\"title\":\"Rate limit exceeded\",\"status\":" + rule.getStatus()
                                + "}"
                        : rule.getBody();
                CompiledRule result = new CompiledRule(
                        rule.getId(),
                        rule.isEnabled(),
                        rule.getStage(),
                        rule.getPath() == null ? null : new PathPatternParser().parse(rule.getPath()),
                        Set.copyOf(methods),
                        rule.getHandler(),
                        rule.getScope() == null
                                ? (rule.getStage() == Stage.MVC ? Scope.HANDLER : Scope.RULE)
                                : rule.getScope(),
                        new RateLimitPolicy(rule.getCapacity(), rule.getRefillTokens(), rule.getRefillPeriod()),
                        store,
                        resolver,
                        rule.getKeyHeader(),
                        rule.getMissingKey(),
                        rule.getFailureMode() == null
                                ? Objects.requireNonNull(properties.getFailureMode())
                                : rule.getFailureMode(),
                        rule.getStatus(),
                        rule.getContentType(),
                        body);
                if (ids.putIfAbsent(result.id(), result) != null) throw new IllegalArgumentException("Duplicate id");
                compiled.add(result);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "rose.ratelimit.rules[" + i + "] (" + rule.getId() + "): " + e.getMessage(), e);
            }
        }
        boolean redis = properties.getStore().getType().equals("redis")
                || compiled.stream().anyMatch(r -> r.store().name().equals("redis"));
        String resolved = properties.getNamespace() == null ? applicationName : properties.getNamespace();
        if (resolved == null || resolved.isBlank()) {
            if (redis)
                throw new IllegalArgumentException(
                        "Redis requires rose.ratelimit.namespace or spring.application.name");
            resolved = "application";
        }
        return new RateLimitRules(resolved, compiled);
    }
}
