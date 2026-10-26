package io.zhijun.ratelimit.sample;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {
    private final ReportMapper reportMapper;

    public ReportController(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @GetMapping("/api/reports/count")
    public Map<String, Long> count() {
        return Map.of("rows", reportMapper.countRows());
    }
}
