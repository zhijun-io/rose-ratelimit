package io.zhijun.ratelimit.mybatis;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

public final class RateLimitMybatisBindingInitializer implements SmartInitializingSingleton {
    private final RateLimitRules rules;
    private final ApplicationContext context;
    private final RateLimitProperties properties;

    public RateLimitMybatisBindingInitializer(
            RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        this.rules = rules;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getMybatis().isEnabled()) {
            return;
        }
        RateLimitMybatisBinder.bind(rules, context, properties);
        if (!context.getBeansOfType(RateLimitMybatisInterceptor.class).isEmpty()) {
            RateLimitMybatisInterceptor interceptor = context.getBean(RateLimitMybatisInterceptor.class);
            for (SqlSessionFactory factory :
                    context.getBeansOfType(SqlSessionFactory.class).values()) {
                if (!factory.getConfiguration().getInterceptors().contains(interceptor)) {
                    factory.getConfiguration().addInterceptor(interceptor);
                }
            }
        }
    }
}
