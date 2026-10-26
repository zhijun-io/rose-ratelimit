package io.zhijun.ratelimit.autoconfigure;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.web.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class RateLimitIntegrationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitMvcAutoConfiguration.class,
                    RateLimitMetricsAutoConfiguration.class));
    private final String[] base = {
        "rose.ratelimit.rules[0].id=one", "rose.ratelimit.rules[0].path=/api/**",
        "rose.ratelimit.rules[0].capacity=1", "rose.ratelimit.rules[0].refill-tokens=1",
        "rose.ratelimit.rules[0].refill-period=1d"
    };

    @Test
    void noBusinessAnnotationIsNeededToProtectFilterEndpoints() {
        runner.withPropertyValues(base).run(context -> {
            assertThat(context).hasNotFailed();
            var filter = context.getBean(FilterRegistrationBean.class).getFilter();
            var first = new MockHttpServletResponse();
            filter.doFilter(request("/api/login"), first, new MockFilterChain());
            assertThat(first.getStatus()).isEqualTo(200);
            var second = new MockHttpServletResponse();
            filter.doFilter(request("/api/login"), second, new MockFilterChain());
            assertThat(second.getStatus()).isEqualTo(429);
            assertThat(second.getHeader("Retry-After")).isNotBlank();
            assertThat(second.getContentAsString()).contains("\"status\":429");
            var unmatched = new MockHttpServletResponse();
            filter.doFilter(request("/health"), unmatched, new MockFilterChain());
            assertThat(unmatched.getStatus()).isEqualTo(200);
        });
    }

    @Test
    void ruleOrderAndMethodsSelectOnlyFirstMatch() {
        runner.withPropertyValues(base)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].methods=POST",
                        "rose.ratelimit.rules[1].id=second",
                        "rose.ratelimit.rules[1].path=/api/**",
                        "rose.ratelimit.rules[1].capacity=2")
                .run(context -> {
                    var rules = context.getBean(RateLimitRules.class);
                    assertThat(rules.select(request("/api/test"), RateLimitOptions.Stage.FILTER, null)
                                    .id())
                            .isEqualTo("second");
                    var post = request("/api/test");
                    post.setMethod("POST");
                    assertThat(rules.select(post, RateLimitOptions.Stage.FILTER, null)
                                    .id())
                            .isEqualTo("one");
                });
    }

    @Test
    void disabledOrNonWebContextsHaveNoLimiter() {
        runner.withPropertyValues("rose.ratelimit.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(RateLimitExecutor.class)
                        .doesNotHaveBean(RateLimitStore.class));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RateLimitExecutor.class));
    }

    @Test
    void pureServletApplicationDoesNotRequireMvc() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.web.servlet"))
                .withPropertyValues(base)
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(FilterRegistrationBean.class)
                            .doesNotHaveBean("rateLimitInterceptor");
                });
    }

    @Test
    void mvcRulesFailClearlyIfMvcIsMissing() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.web.servlet"))
                .withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].stage=MVC")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("MVC rules require Spring MVC"));
    }

    @Test
    void unknownPluginsAndDuplicateNamesFailAtStartup() {
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].key-source=unknown")
                .run(context ->
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("Missing key resolver"));
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.store.type=redis")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("starter-redis"));
        runner.withBean("duplicate", RateLimitKeyResolver.class, () -> resolver("ip", "same"))
                .run(context ->
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("Duplicate rate-limit plugin"));
    }

    @Test
    void customResolverIsAcceptedAndMissingKeysAreExplicit() {
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].key-source=custom")
                .withBean("custom", RateLimitKeyResolver.class, () -> resolver("custom", "client"))
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].key-source=principal")
                .run(context -> {
                    var response = new MockHttpServletResponse();
                    context.getBean(FilterRegistrationBean.class)
                            .getFilter()
                            .doFilter(request("/api/login"), response, new MockFilterChain());
                    assertThat(response.getStatus()).isEqualTo(400);
                });
        runner.withPropertyValues(base)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].key-source=principal", "rose.ratelimit.rules[0].missing-key=shared")
                .run(context -> {
                    var response = new MockHttpServletResponse();
                    context.getBean(FilterRegistrationBean.class)
                            .getFilter()
                            .doFilter(request("/api/login"), response, new MockFilterChain());
                    assertThat(response.getStatus()).isEqualTo(200);
                });
    }

    @Test
    void onlyTypedBackendFailuresUsePerRuleFailureMode() {
        for (String mode : new String[] {"open", "closed"}) {
            runner.withPropertyValues(base)
                    .withPropertyValues(
                            "rose.ratelimit.rules[0].store=broken", "rose.ratelimit.rules[0].failure-mode=" + mode)
                    .withBean("broken", RateLimitStore.class, () -> new RateLimitStore() {
                        public String name() {
                            return "broken";
                        }

                        public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                            throw new RateLimitBackendException("offline");
                        }
                    })
                    .run(context -> {
                        var response = new MockHttpServletResponse();
                        context.getBean(FilterRegistrationBean.class)
                                .getFilter()
                                .doFilter(request("/api/login"), response, new MockFilterChain());
                        assertThat(response.getStatus()).isEqualTo(mode.equals("open") ? 200 : 503);
                    });
        }
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].key-source=broken")
                .withBean("broken", RateLimitKeyResolver.class, () -> new RateLimitKeyResolver() {
                    public String name() {
                        return "broken";
                    }

                    public String resolve(HttpServletRequest request, String header) {
                        throw new IllegalStateException("bug");
                    }
                })
                .run(context -> assertThatThrownBy(() -> context.getBean(FilterRegistrationBean.class)
                                .getFilter()
                                .doFilter(request("/api/login"), new MockHttpServletResponse(), new MockFilterChain()))
                        .isInstanceOf(IllegalStateException.class));
    }

    @Test
    void repeatedExecutionOnlyConsumesOnceAndRetryRoundsUp() {
        AtomicInteger calls = new AtomicInteger();
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].store=probe")
                .withBean("probe", RateLimitStore.class, () -> new RateLimitStore() {
                    public String name() {
                        return "probe";
                    }

                    public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                        calls.incrementAndGet();
                        return RateLimitDecision.rejected(Duration.ofMillis(1900));
                    }
                })
                .run(context -> {
                    var request = request("/api/test");
                    var executor = context.getBean(RateLimitExecutor.class);
                    var rule =
                            context.getBean(RateLimitRules.class).select(request, RateLimitOptions.Stage.FILTER, null);
                    var response = new MockHttpServletResponse();
                    executor.execute(request, response, rule, null);
                    request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
                    executor.execute(request, new MockHttpServletResponse(), rule, null);
                    assertThat(calls.get()).isEqualTo(1);
                    assertThat(response.getHeader("Retry-After")).isEqualTo("2");
                });
    }

    @Test
    void mvcPoliciesAutomaticallySeparateHandlersWithoutAnnotations() {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].stage=mvc", "rose.ratelimit.filter-enabled=false")
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/a")).andExpect(status().isOk());
                    mvc.perform(get("/api/a")).andExpect(status().isTooManyRequests());
                    mvc.perform(get("/api/b")).andExpect(status().isOk());
                });
    }

    @Test
    void annotationAndExternalMvcMatchUseTheSameRuleOnce() {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].stage=mvc", "rose.ratelimit.filter-enabled=false")
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/annotated")).andExpect(status().isOk());
                    mvc.perform(get("/api/annotated")).andExpect(status().isTooManyRequests());
                });
    }

    @Test
    void filterAndMvcApplyDistinctRulesAndMetricsCountDecisions() {
        runner.withUserConfiguration(Mvc.class)
                .withBean(SimpleMeterRegistry.class)
                .withPropertyValues(base)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].id=entrance",
                        "rose.ratelimit.rules[0].capacity=3",
                        "rose.ratelimit.rules[1].id=one",
                        "rose.ratelimit.rules[1].stage=mvc",
                        "rose.ratelimit.rules[1].path=/api/**",
                        "rose.ratelimit.rules[1].capacity=1",
                        "rose.ratelimit.rules[1].refill-period=1d")
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                            .addFilters(context.getBean(FilterRegistrationBean.class)
                                    .getFilter())
                            .build();
                    mvc.perform(get("/api/a")).andExpect(status().isOk());
                    mvc.perform(get("/api/a")).andExpect(status().isTooManyRequests());
                    assertThat(context.getBean(SimpleMeterRegistry.class)
                                    .get("rose.ratelimit.requests")
                                    .tags("rule", "one", "result", "rejected")
                                    .counter()
                                    .count())
                            .isEqualTo(1);
                });
    }

    @Test
    void invalidAnnotationReferenceFailsStartup() {
        runner.withUserConfiguration(Mvc.class)
                .run(context ->
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("@RateLimit must reference"));
    }

    @Test
    void asyncMvcRedispatchDoesNotConsumeTwice() {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].stage=mvc")
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    var pending = mvc.perform(get("/api/async"))
                            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request()
                                    .asyncStarted())
                            .andReturn();
                    mvc.perform(asyncDispatch(pending)).andExpect(status().isOk());
                    mvc.perform(get("/api/async")).andExpect(status().isTooManyRequests());
                });
    }

    @Test
    void annotationOnlyPolicyDoesNotAutomaticallyLimitOtherRoutes() {
        runner.withUserConfiguration(Mvc.class)
                .withPropertyValues(
                        "rose.ratelimit.rules[0].id=one", "rose.ratelimit.rules[0].stage=mvc",
                        "rose.ratelimit.rules[0].capacity=1", "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mvc.perform(get("/api/a")).andExpect(status().isOk());
                    mvc.perform(get("/api/a")).andExpect(status().isOk());
                    mvc.perform(get("/api/annotated")).andExpect(status().isOk());
                    mvc.perform(get("/api/annotated")).andExpect(status().isTooManyRequests());
                });
    }

    @Test
    void invalidPathsAndHeadersFailBeforeTrafficAndDisabledRulesDoNotApply() {
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].path=/api/**/invalid")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("rules[0]"));
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].key-source=header")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("key-header"));
        runner.withPropertyValues(base)
                .withPropertyValues("rose.ratelimit.rules[0].enabled=false")
                .run(context -> assertThat(context.getBean(RateLimitRules.class)
                                .select(request("/api/a"), RateLimitOptions.Stage.FILTER, null))
                        .isNull());
    }

    @Test
    void downstreamFailureIsNeverRetriedByLimiter() {
        AtomicInteger calls = new AtomicInteger();
        runner.withPropertyValues(base).run(context -> {
            var filter = context.getBean(FilterRegistrationBean.class).getFilter();
            assertThatThrownBy(() -> filter.doFilter(request("/api/a"), new MockHttpServletResponse(), (req, res) -> {
                        calls.incrementAndGet();
                        throw new IllegalStateException("business failed");
                    }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(calls.get()).isEqualTo(1);
        });
    }

    private static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static RateLimitKeyResolver resolver(String name, String value) {
        return new RateLimitKeyResolver() {
            public String name() {
                return name;
            }

            public String resolve(HttpServletRequest request, String header) {
                return value;
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Mvc {
        @Bean
        Controller controller() {
            return new Controller();
        }
    }

    @RestController
    static class Controller {
        @GetMapping("/api/a")
        String a() {
            return "a";
        }

        @GetMapping("/api/b")
        String b() {
            return "b";
        }

        @GetMapping("/api/async")
        java.util.concurrent.Callable<String> async() {
            return () -> "async";
        }

        @RateLimit("one")
        @GetMapping("/api/annotated")
        String annotated() {
            return "annotated";
        }
    }
}
