package io.zhijun.ratelimit.restclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zhijun.ratelimit.web.RateLimitRules;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RateLimitRestClientInterceptorTest {
    private final RateLimitRestClientInterceptor interceptor =
            new RateLimitRestClientInterceptor(new RateLimitRules("test", List.of()), null);

    @Test
    void sendsRequestWhenNoRestClientRuleMatches() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://EXAMPLE.com/api/items"));
        var sent = new AtomicBoolean();

        var response = interceptor.intercept(request, new byte[0], (ignoredRequest, ignoredBody) -> {
            sent.set(true);
            return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);
        });

        assertThat(sent).isTrue();
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
    }

    @Test
    void rejectsRelativeUriBeforeSendingRequest() {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("/api/items"));
        var sent = new AtomicBoolean();

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], (ignoredRequest, ignoredBody) -> {
                    sent.set(true);
                    return new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute request URI");
        assertThat(sent).isFalse();
    }
}
