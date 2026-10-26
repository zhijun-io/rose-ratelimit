package io.zhijun.ratelimit.mybatis;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

@Intercepts({
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public final class RateLimitMybatisInterceptor implements Interceptor {
    private final RateLimitRules rules;
    private final RateLimitMybatisExecutor executor;
    private final RateLimitProperties properties;

    public RateLimitMybatisInterceptor(
            RateLimitRules rules, RateLimitMybatisExecutor executor, RateLimitProperties properties) {
        this.rules = rules;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        String statementId = mappedStatement.getId();
        CompiledRule rule = rules.externalMybatisRule(statementId);
        if (rule == null
                && rules.mybatisIndexReady()
                && !properties.getMybatis().isRequireStatementMatch()) {
            rule = rules.selectMybatis(statementId);
        }
        return executor.invokeGuarded(rule, statementId, () -> proceed(invocation));
    }

    private static Object proceed(Invocation invocation) throws Exception {
        try {
            return invocation.proceed();
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

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}
}
