package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.web.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfiguration(after = RateLimitAutoConfiguration.class)
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@ConditionalOnBean(RateLimitExecutor.class)
@ConditionalOnProperty(prefix = "rose.ratelimit", name = "mvc-enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitMvcAutoConfiguration {
    @Bean
    RateLimitMvcBindingInitializer rateLimitMvcBindingInitializer(
            RateLimitRules rules,
            RateLimitProperties properties,
            ObjectProvider<RequestMappingHandlerMapping> mappings) {
        return new RateLimitMvcBindingInitializer(rules, properties, mappings);
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor(
            RateLimitRules rules, RateLimitExecutor executor, RateLimitProperties properties) {
        return new RateLimitInterceptor(rules, executor, properties);
    }

    @Bean
    WebMvcConfigurer rateLimitMvcConfigurer(RateLimitInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).order(Integer.MIN_VALUE + 10);
            }
        };
    }
}
