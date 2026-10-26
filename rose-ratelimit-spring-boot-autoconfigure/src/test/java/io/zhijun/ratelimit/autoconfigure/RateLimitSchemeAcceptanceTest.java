package io.zhijun.ratelimit.autoconfigure;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitRules;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** §9.3 acceptance cases (T-FILTER-PATH-01, T-MVC-STRICT-01/02). */
class RateLimitSchemeAcceptanceTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitMvcAutoConfiguration.class));

    @Test
    void tFilterPath01_dotSegmentPathSharesFilterQuotaWithCanonicalPath() {
        String[] props = {
            "rose.ratelimit.rules[0].id=auth",
            "rose.ratelimit.rules[0].path=/api/auth/**",
            "rose.ratelimit.rules[0].capacity=1",
            "rose.ratelimit.rules[0].refill-tokens=1",
            "rose.ratelimit.rules[0].refill-period=1d"
        };
        runner.withPropertyValues(props).run(context -> {
            var filter = context.getBean(FilterRegistrationBean.class).getFilter();
            var canonical = filterRequest(filter, servletRequest("GET", "/api/auth/login"));
            assertThat(canonical.getStatus()).isEqualTo(200);
            var dotted = filterRequest(filter, servletRequest("GET", "/api/auth/./login"));
            assertThat(dotted.getStatus()).isEqualTo(429);
        });
    }

    @Test
    void tMvcStrict01_unmappedMvcRuleFailsStartupWhenStrict() {
        runner.withUserConfiguration(MvcEndpoints.class)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].id=ghost",
                        "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].path=/no/such/**",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.mvc.require-handler-match=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("ghost")
                        .hasMessageContaining("handler mapping"));
    }

    @Test
    void tMvcStrict02_unmappedMvcRuleWarnsWhenNotStrict() {
        runner.withUserConfiguration(MvcEndpoints.class)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].id=ghost",
                        "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].path=/no/such/**",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.mvc.require-handler-match=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimitRules.class).mvcIndexReady())
                            .isTrue();
                });
    }

    @Test
    void tMvcIndex01_strictModeUsesStartupBindingForMappedHandler() throws Exception {
        runner.withUserConfiguration(MvcEndpoints.class)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].id=one",
                        "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].path=/api/hit/**",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d",
                        "rose.ratelimit.filter-enabled=false")
                .run(context -> {
                    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    try {
                        mvc.perform(get("/api/hit")).andExpect(status().isOk());
                        mvc.perform(get("/api/hit")).andExpect(status().isTooManyRequests());
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                });
    }

    private static MockHttpServletResponse filterRequest(jakarta.servlet.Filter filter, HttpServletRequest request)
            throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest servletRequest(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        request.setContextPath("");
        request.setServletPath(path);
        request.setRequestURI(path);
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcEndpoints {
        @Bean
        HitController hitController() {
            return new HitController();
        }
    }

    @RestController
    static class HitController {
        @GetMapping("/api/hit")
        String hit() {
            return "ok";
        }
    }
}
