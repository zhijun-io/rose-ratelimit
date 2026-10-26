package io.zhijun.ratelimit.sample;

import io.zhijun.ratelimit.web.RateLimit;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {
    @RateLimit("export")
    @GetMapping("/api/export")
    public Map<String, String> export() {
        return Map.of("result", "export started");
    }
}
