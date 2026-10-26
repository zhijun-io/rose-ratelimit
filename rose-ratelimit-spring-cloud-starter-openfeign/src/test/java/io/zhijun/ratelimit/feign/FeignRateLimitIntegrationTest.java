package io.zhijun.ratelimit.feign;

import static org.assertj.core.api.Assertions.*;

import feign.FeignException;
import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.feign.autoconfigure.RateLimitFeignAutoConfiguration;
import io.zhijun.ratelimit.feign.autoconfigure.RateLimitFeignResolverAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

class FeignRateLimitIntegrationTest {
    private MockWebServer server;
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitFeignResolverAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitFeignAutoConfiguration.class,
                    FeignAutoConfiguration.class));

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void shutdownServer() throws IOException {
        server.shutdown();
    }

    @Test
    void feignClientMethodIsRateLimited() {
        server.enqueue(new MockResponse().setBody("ok"));
        server.enqueue(new MockResponse().setBody("ok"));
        String baseUrl = server.url("/").toString();
        String selector = DemoClient.class.getName() + "#hello";

        runner.withUserConfiguration(FeignApp.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "spring.cloud.openfeign.lazy-attributes-resolution=true",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=feign-demo",
                        "rose.ratelimit.rules[0].stage=FEIGN",
                        "rose.ratelimit.rules[0].target=feign:*DemoClient#hello",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d",
                        "demo.url=" + baseUrl)
                .run(context -> {
                    assertThat(context.getBean(RateLimitRules.class).feignIndexReady())
                            .isTrue();
                    assertThat(context.getBean(RateLimitRules.class).externalFeignRule(selector))
                            .isNotNull();
                    DemoClient client = context.getBean(DemoClient.class);
                    assertThat(client.hello()).isEqualTo("ok");
                    assertThatThrownBy(client::hello).isInstanceOf(FeignException.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = DemoClient.class)
    static class FeignApp {}

    @FeignClient(name = "demo", url = "${demo.url}")
    interface DemoClient {
        @GetMapping("/hello")
        String hello();
    }
}
