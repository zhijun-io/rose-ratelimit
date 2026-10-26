package io.zhijun.ratelimit.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates MVC {@code composite} with circuit-breaker (rule {@code fragile-cb}). Call with
 * {@code ?fail=true} repeatedly to open the circuit and receive 503.
 */
@RestController
public class FragileController {
    @GetMapping("/api/fragile/ping")
    public String ping(@RequestParam(name = "fail", defaultValue = "false") boolean fail) {
        if (fail) {
            throw new IllegalStateException("simulated downstream failure");
        }
        return "pong";
    }
}
