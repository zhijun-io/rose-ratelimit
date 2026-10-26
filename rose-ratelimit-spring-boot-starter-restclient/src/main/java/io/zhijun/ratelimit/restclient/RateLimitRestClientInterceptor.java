package io.zhijun.ratelimit.restclient;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitGuardedInvocation;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.server.PathContainer;

/** Applies REST_CLIENT rules before the underlying synchronous HTTP request is sent. */
public final class RateLimitRestClientInterceptor implements ClientHttpRequestInterceptor {
    private final RateLimitRules rules;
    private final RateLimitGuardedInvocation guardedInvocation;

    public RateLimitRestClientInterceptor(RateLimitRules rules, RateLimitGuardedInvocation guardedInvocation) {
        this.rules = rules;
        this.guardedInvocation = guardedInvocation;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI uri = request.getURI();
        String origin = origin(uri);
        String path = uri.getPath();
        PathContainer pathContainer = PathContainer.parsePath(path == null || path.isEmpty() ? "/" : path);
        String method = request.getMethod().name();
        CompiledRule rule = rules.select(pathContainer, method, Stage.REST_CLIENT, origin);
        if (rule == null) {
            return execution.execute(request, body);
        }
        try {
            return guardedInvocation.invokeGuarded(
                    rule, origin, () -> execution.execute(request, body), RestClientRateLimitStageSemantics.INSTANCE);
        } catch (IOException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while applying RestClient rate-limit rule '" + rule.id() + "'", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("RestClient request failed under rate-limit rule '" + rule.id() + "'", e);
        }
    }

    private static String origin(URI uri) {
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if (scheme == null || authority == null) {
            throw new IllegalArgumentException("RestClient rate limiting requires an absolute request URI");
        }
        int userInfoEnd = authority.lastIndexOf('@');
        if (userInfoEnd >= 0) {
            authority = authority.substring(userInfoEnd + 1);
        }
        return scheme.toLowerCase(Locale.ROOT) + "://" + authority.toLowerCase(Locale.ROOT);
    }
}
