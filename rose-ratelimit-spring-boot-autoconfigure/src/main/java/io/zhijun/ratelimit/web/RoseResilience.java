package io.zhijun.ratelimit.web;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import java.util.concurrent.Callable;
import org.springframework.util.PatternMatchUtils;

/**
 * Programmatic entry for {@code stage: INVOKE} rules. Guard runs before {@code action}; rejections throw
 * {@link RateLimitInvokeRejectedException}.
 */
public final class RoseResilience {
    private final RateLimitRules rules;
    private final RateLimitInvokeExecutor executor;
    private final RateLimitProperties properties;

    public RoseResilience(RateLimitRules rules, RateLimitInvokeExecutor executor, RateLimitProperties properties) {
        this.rules = rules;
        this.executor = executor;
        this.properties = properties;
    }

    public void run(String ruleId, Runnable action) {
        call(ruleId, () -> {
            action.run();
            return null;
        });
    }

    public <T> T call(String ruleId, Callable<T> action) {
        return call(ruleId, null, action);
    }

    public <T> T call(String ruleId, String invokeSelector, Callable<T> action) {
        if (!properties.getInvoke().isEnabled()) {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        CompiledRule rule = rules.rule(ruleId);
        if (!rule.enabled() || rule.stage() != Stage.INVOKE) {
            throw new IllegalArgumentException("Rule '" + ruleId + "' is not an enabled INVOKE rule");
        }
        String selector = resolveSelector(rule, invokeSelector);
        try {
            return executor.invokeGuarded(rule, selector, action);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resolveSelector(CompiledRule rule, String invokeSelector) {
        String pattern = rule.handler();
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalStateException("INVOKE rule '" + rule.id() + "' has no target/handler pattern");
        }
        if (invokeSelector == null || invokeSelector.isBlank()) {
            return pattern;
        }
        if (!PatternMatchUtils.simpleMatch(pattern, invokeSelector)) {
            throw new IllegalArgumentException(
                    "Invoke selector '" + invokeSelector + "' does not match rule pattern '" + pattern + "'");
        }
        return invokeSelector;
    }
}
