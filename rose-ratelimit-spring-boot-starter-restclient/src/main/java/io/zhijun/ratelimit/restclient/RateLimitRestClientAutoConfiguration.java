package io.zhijun.ratelimit.restclient;

import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitRules;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@AutoConfiguration(afterName = "io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration")
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class, ClientHttpRequestInterceptor.class})
@ConditionalOnProperty(
        prefix = "rose.ratelimit.rest-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RateLimitRestClientAutoConfiguration {
    @Bean
    @ConditionalOnBean({RateLimitRules.class, RateLimitGuardedInvocation.class})
    RestClientCustomizer rateLimitRestClientCustomizer(
            RateLimitRules rules, RateLimitGuardedInvocation guardedInvocation) {
        RateLimitRestClientInterceptor interceptor = new RateLimitRestClientInterceptor(rules, guardedInvocation);
        return builder -> builder.requestInterceptor(interceptor);
    }
}
