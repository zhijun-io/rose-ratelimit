package io.zhijun.ratelimit.mybatis;

import io.zhijun.ratelimit.autoconfigure.RateLimitProperties;
import io.zhijun.ratelimit.web.CompiledRule;
import io.zhijun.ratelimit.web.RateLimitOptions.Stage;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.util.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/** Binds MYBATIS rules to mapped statement ids at startup. */
final class RateLimitMybatisBinder {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitMybatisBinder.class);

    private RateLimitMybatisBinder() {}

    static void bind(RateLimitRules rules, ApplicationContext context, RateLimitProperties properties) {
        Map<String, CompiledRule> index = new HashMap<>();
        for (SqlSessionFactory factory :
                context.getBeansOfType(SqlSessionFactory.class).values()) {
            Configuration configuration = factory.getConfiguration();
            for (String statementId : configuration.getMappedStatementNames()) {
                CompiledRule hit = rules.selectMybatis(statementId);
                if (hit != null) {
                    index.merge(statementId, hit, rules::earlierInConfigOrder);
                }
            }
        }
        rules.bindMybatisIndex(index);
        validateRuleCoverage(rules, index, properties.getMybatis().isRequireStatementMatch());
    }

    private static void validateRuleCoverage(
            RateLimitRules rules, Map<String, CompiledRule> index, boolean requireStatementMatch) {
        Set<String> boundRuleIds = new HashSet<>();
        index.values().forEach(rule -> boundRuleIds.add(rule.id()));
        for (CompiledRule rule : rules.rules()) {
            if (!rule.enabled() || rule.stage() != Stage.MYBATIS) continue;
            if (rule.handler() == null) continue;
            if (boundRuleIds.contains(rule.id())) continue;
            if (requireStatementMatch) {
                throw new IllegalArgumentException(
                        "MYBATIS rule '" + rule.id() + "' did not match any mapped statement");
            }
            LOG.warn("MYBATIS rule '{}' did not match any mapped statement", rule.id());
        }
    }
}
