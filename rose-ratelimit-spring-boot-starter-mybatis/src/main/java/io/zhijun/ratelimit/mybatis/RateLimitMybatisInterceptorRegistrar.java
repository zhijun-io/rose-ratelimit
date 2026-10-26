package io.zhijun.ratelimit.mybatis;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** Registers {@link RateLimitMybatisInterceptor} on every {@link SqlSessionFactory} bean. */
public final class RateLimitMybatisInterceptorRegistrar implements BeanPostProcessor {
    private final Interceptor interceptor;

    public RateLimitMybatisInterceptorRegistrar(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SqlSessionFactory factory) {
            org.apache.ibatis.session.Configuration configuration = factory.getConfiguration();
            if (!configuration.getInterceptors().contains(interceptor)) {
                configuration.addInterceptor(interceptor);
            }
        }
        return bean;
    }
}
