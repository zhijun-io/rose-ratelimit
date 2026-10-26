package io.zhijun.ratelimit.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.PreparedStatementProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.sql.SQLException;

/** Druid filter that rate-limits SQL before execution. */
public final class RateLimitDruidFilter extends FilterAdapter {
    private final RateLimitRules rules;
    private final RateLimitJdbcExecutor executor;

    public RateLimitDruidFilter(RateLimitRules rules, RateLimitJdbcExecutor executor) {
        this.rules = rules;
        this.executor = executor;
    }

    @Override
    public boolean preparedStatement_execute(FilterChain chain, PreparedStatementProxy statement) throws SQLException {
        return runGuarded(statement.getSql(), () -> chain.preparedStatement_execute(statement));
    }

    @Override
    public ResultSetProxy preparedStatement_executeQuery(FilterChain chain, PreparedStatementProxy statement)
            throws SQLException {
        return runGuarded(statement.getSql(), () -> chain.preparedStatement_executeQuery(statement));
    }

    @Override
    public int preparedStatement_executeUpdate(FilterChain chain, PreparedStatementProxy statement)
            throws SQLException {
        return runGuarded(statement.getSql(), () -> chain.preparedStatement_executeUpdate(statement));
    }

    @Override
    public boolean statement_execute(FilterChain chain, StatementProxy statement, String sql) throws SQLException {
        return runGuarded(sql, () -> chain.statement_execute(statement, sql));
    }

    @Override
    public ResultSetProxy statement_executeQuery(FilterChain chain, StatementProxy statement, String sql)
            throws SQLException {
        return runGuarded(sql, () -> chain.statement_executeQuery(statement, sql));
    }

    @Override
    public int statement_executeUpdate(FilterChain chain, StatementProxy statement, String sql) throws SQLException {
        return runGuarded(sql, () -> chain.statement_executeUpdate(statement, sql));
    }

    private <T> T runGuarded(String sql, SqlCall<T> call) throws SQLException {
        JdbcRuleMatcher.Hit hit = JdbcRuleMatcher.match(rules, sql);
        if (hit == null) {
            return call.run();
        }
        try {
            return executor.invokeGuarded(hit.rule(), hit.selector(), call::run);
        } catch (SQLException sqlException) {
            throw sqlException;
        } catch (Exception exception) {
            throw new SQLException(exception);
        }
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T run() throws SQLException;
    }
}
