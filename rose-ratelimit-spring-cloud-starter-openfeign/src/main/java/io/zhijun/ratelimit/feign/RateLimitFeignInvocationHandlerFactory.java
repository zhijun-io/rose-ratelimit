package io.zhijun.ratelimit.feign;

import feign.InvocationHandlerFactory;
import feign.Target;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public final class RateLimitFeignInvocationHandlerFactory implements InvocationHandlerFactory {
    private final InvocationHandlerFactory delegate = new Default();
    private final RateLimitRules rules;
    private final RateLimitFeignExecutor executor;
    private final RateLimitProperties properties;

    public RateLimitFeignInvocationHandlerFactory(
            RateLimitRules rules, RateLimitFeignExecutor executor, RateLimitProperties properties) {
        this.rules = rules;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    public InvocationHandler create(Target target, Map<Method, InvocationHandlerFactory.MethodHandler> dispatch) {
        InvocationHandler handler = delegate.create(target, dispatch);
        return new RateLimitFeignInvocationHandler(rules, executor, properties, target.type(), handler);
    }

    private static final class RateLimitFeignInvocationHandler implements InvocationHandler {
        private final RateLimitRules rules;
        private final RateLimitFeignExecutor executor;
        private final RateLimitProperties properties;
        private final Class<?> type;
        private final InvocationHandler delegate;

        RateLimitFeignInvocationHandler(
                RateLimitRules rules,
                RateLimitFeignExecutor executor,
                RateLimitProperties properties,
                Class<?> type,
                InvocationHandler delegate) {
            this.rules = rules;
            this.executor = executor;
            this.properties = properties;
            this.type = type;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return delegate.invoke(proxy, method, args);
            }
            String selector = type.getName() + "#" + method.getName();
            CompiledRule rule = rules.externalFeignRule(selector);
            if (rule == null
                    && rules.feignIndexReady()
                    && !properties.getFeign().isRequireClientMatch()) {
                rule = rules.selectFeign(selector);
            }
            return executor.invokeGuarded(rule, selector, () -> invokeDelegate(proxy, method, args));
        }

        private Object invokeDelegate(Object proxy, Method method, Object[] args) throws Exception {
            try {
                return delegate.invoke(proxy, method, args);
            } catch (Throwable throwable) {
                if (throwable instanceof Exception exception) {
                    throw exception;
                }
                if (throwable instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(throwable);
            }
        }
    }
}
