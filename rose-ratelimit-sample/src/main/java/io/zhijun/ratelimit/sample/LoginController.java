package io.zhijun.ratelimit.sample;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
    @PostMapping("/api/auth/login")
    public Map<String, String> login() {
        return Map.of("result", "demo login endpoint; no authentication is performed");
    }
}
