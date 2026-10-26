package io.zhijun.ratelimit.druid;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

public final class RateLimitDruidBindingInitializer implements SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final ApplicationContext context;
    private final RateLimitProperties properties;
    private final RateLimitDruidFilter filter;

    public RateLimitDruidBindingInitializer(
            RateLimitRules rules,
            ApplicationContext context,
            RateLimitProperties properties,
            RateLimitDruidFilter filter) {
        this.rules = rules;
        this.context = context;
        this.properties = properties;
        this.filter = filter;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getJdbc().isEnabled()) {
            return;
        }
        RateLimitDruidBinder.bind(rules, context, properties);
        for (DruidDataSource dataSource :
                context.getBeansOfType(DruidDataSource.class).values()) {
            attachFilter(dataSource);
        }
    }

    private void attachFilter(DruidDataSource dataSource) {
        for (Filter existing : dataSource.getProxyFilters()) {
            if (existing instanceof RateLimitDruidFilter) {
                return;
            }
        }
        dataSource.getProxyFilters().add(filter);
    }
}
