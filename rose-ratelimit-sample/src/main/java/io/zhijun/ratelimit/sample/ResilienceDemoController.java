package io.zhijun.ratelimit.sample;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FILTER-stage resilience demos ({@code demo-retry}, {@code demo-bulkhead} in application.yml). */
@RestController
public class ResilienceDemoController {
    static final String RETRY_ATTEMPT_ATTR = ResilienceDemoController.class.getName() + ".retryAttempt";

    @GetMapping("/api/demo/retry")
    public Map<String, Object> retry(HttpServletRequest request) {
        Object seen = request.getAttribute(RETRY_ATTEMPT_ATTR);
        int attempt = seen == null ? 0 : (Integer) seen;
        request.setAttribute(RETRY_ATTEMPT_ATTR, attempt + 1);
        if (attempt < 2) {
            throw new IllegalStateException("transient failure on attempt " + (attempt + 1));
        }
        return Map.of("status", "ok", "attempts", attempt + 1);
    }

    @GetMapping("/api/demo/work")
    public Map<String, String> work() {
        return Map.of("status", "ok");
    }
}
