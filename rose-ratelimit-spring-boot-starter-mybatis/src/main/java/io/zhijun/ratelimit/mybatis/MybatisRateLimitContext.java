package io.zhijun.ratelimit.mybatis;

/** Thread-local statement id for {@link MybatisRateLimitKeyResolver} during a guarded call. */
public final class MybatisRateLimitContext {
    private static final ThreadLocal<String> STATEMENT_ID = new ThreadLocal<>();

    private MybatisRateLimitContext() {}

    static void enter(String statementId) {
        STATEMENT_ID.set(statementId);
    }

    static void exit() {
        STATEMENT_ID.remove();
    }

    public static String currentStatementId() {
        return STATEMENT_ID.get();
    }
}
