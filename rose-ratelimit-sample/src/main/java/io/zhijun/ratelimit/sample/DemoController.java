package io.zhijun.ratelimit.sample;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    @GetMapping("/api/items")
    public List<String> items() {
        return List.of("notebook", "pen");
    }

    @PostMapping("/api/items")
    public Map<String, String> createItem() {
        return Map.of("result", "created");
    }
}
