package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Binds MVC rules to handler selectors at startup; see docs/development-prompt.md §4.4.2. */
final class RateLimitMvcBinder {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitMvcBinder.class);

    private static final Set<RequestMethod> ALL_METHODS = EnumSet.of(
            RequestMethod.GET,
            RequestMethod.HEAD,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE,
            RequestMethod.OPTIONS,
            RequestMethod.TRACE);

    private RateLimitMvcBinder() {}

    static void bind(
            RateLimitRules rules,
            ObjectProvider<RequestMappingHandlerMapping> mappings,
            RateLimitProperties properties) {
        Map<String, CompiledRule> index = new HashMap<>();
        mappings.orderedStream()
                .forEach(mapping -> mapping.getHandlerMethods().forEach((info, handlerMethod) -> {
                    String selector = selector(handlerMethod);
                    CompiledRule chosen = null;
                    Set<String> paths = info.getPathPatternsCondition().getPatternValues();
                    if (paths.isEmpty()) paths = Set.of("");
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    boolean allMethods = methods.isEmpty();
                    for (String path : paths) {
                        PathContainer pathContainer = PathContainer.parsePath(path);
                        Iterable<RequestMethod> toTry = allMethods ? ALL_METHODS : methods;
                        for (RequestMethod method : toTry) {
                            CompiledRule hit = rules.select(pathContainer, method.name(), Stage.MVC, selector);
                            if (hit != null) {
                                chosen = rules.earlierInConfigOrder(chosen, hit);
                            }
                        }
                    }
                    if (chosen != null) {
                        index.merge(selector, chosen, rules::earlierInConfigOrder);
                    }
                }));
        rules.bindMvcIndex(index);
        validateRuleCoverage(rules, index, properties.getMvc().isRequireHandlerMatch());
    }

    private static void validateRuleCoverage(
            RateLimitRules rules, Map<String, CompiledRule> index, boolean requireHandlerMatch) {
        Set<String> boundRuleIds = new HashSet<>();
        index.values().forEach(rule -> boundRuleIds.add(rule.id()));
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || rule.stage() != Stage.MVC) continue;
            if (rule.path() == null && rule.handler() == null) continue;
            if (boundRuleIds.contains(rule.id())) continue;
            if (requireHandlerMatch) {
                throw new IllegalArgumentException(
                        "MVC rule '" + rule.id() + "' did not match any registered handler mapping");
            }
            LOG.warn("MVC rule '{}' did not match any registered handler mapping", rule.id());
        }
    }

    private static String selector(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getName() + "#"
                + handlerMethod.getMethod().getName();
    }
}
