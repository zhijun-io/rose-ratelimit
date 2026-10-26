package io.zhijun.ratelimit.web;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitRulesMybatisTest {
    @Test
    void selectMybatisMatchesStatementId() {
        CompiledRule rule = mybatisRule("rl", "*UserMapper.selectById");
        RateLimitRules rules = new RateLimitRules("app", List.of(rule));
        assertThat(rules.selectMybatis("com.example.UserMapper.selectById")).isEqualTo(rule);
        assertThat(rules.selectMybatis("com.example.UserMapper.delete")).isNull();
    }

    private static CompiledRule mybatisRule(String id, String handler) {
        RateLimitStore store = new RateLimitStore() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public RateLimitDecision tryAcquire(String key, RateLimitPolicy policy) {
                return RateLimitDecision.allowed(1);
            }
        };
        RateLimitKeyResolver resolver = new RateLimitKeyResolver() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public String resolve(jakarta.servlet.http.HttpServletRequest request, String headerName) {
                return "identity";
            }
        };
        return new CompiledRule(
                id,
                true,
                Stage.MYBATIS,
                null,
                java.util.Set.of(),
                handler,
                Scope.HANDLER,
                new RateLimitPolicy(1, 1, Duration.ofDays(1)),
                store,
                resolver,
                null,
                MissingKey.REJECT,
                FailureMode.OPEN,
                429,
                "application/problem+json",
                "{}",
                ResilienceRuleKind.RATE_LIMIT,
                null,
                503,
                null,
                null,
                null);
    }
}
