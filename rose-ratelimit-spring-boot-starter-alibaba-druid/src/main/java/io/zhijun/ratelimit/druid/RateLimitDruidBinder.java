package io.zhijun.ratelimit.druid;

import com.alibaba.druid.pool.DruidDataSource;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.context.ApplicationContext;

/** Validates JDBC rules against Druid infrastructure at startup. */
final class RateLimitDruidBinder {
    private RateLimitDruidBinder() {}

    static void bind(RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        if (!hasEnabledJdbcRules(rules)) {
            return;
        }
        if (context.getBeansOfType(DruidDataSource.class).isEmpty()
                && properties.getJdbc().isRequireDatasourceMatch()) {
            throw new IllegalArgumentException("JDBC rule requires at least one DruidDataSource bean");
        }
    }

    private static boolean hasEnabledJdbcRules(RateLimitRules rules) {
        for (var rule : rules.rules()) {
            if (rule.enabled() && rule.stage() == Stage.JDBC) {
                return true;
            }
        }
        return false;
    }
}
