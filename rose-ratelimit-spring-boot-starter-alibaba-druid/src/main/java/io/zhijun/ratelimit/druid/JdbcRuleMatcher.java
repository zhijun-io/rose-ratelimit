package io.zhijun.ratelimit.druid;

import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitRules;

/** Picks the earliest matching JDBC rule for a SQL statement. */
final class JdbcRuleMatcher {
    private JdbcRuleMatcher() {}

    record Hit(CompiledRule rule, String selector) {}

    static Hit match(RateLimitRules rules, String sql) {
        CompiledRule chosen = null;
        String chosenSelector = null;
        for (String selector : JdbcSqlTableSelectors.selectors(sql)) {
            CompiledRule hit = rules.selectJdbc(selector);
            if (hit == null) {
                continue;
            }
            if (chosen == null || rules.earlierInConfigOrder(chosen, hit) == hit) {
                chosen = hit;
                chosenSelector = selector;
            }
        }
        if (chosen == null) {
            return null;
        }
        return new Hit(chosen, chosenSelector);
    }
}
