package io.zhijun.ratelimit.druid;

/** Thread-local JDBC selector for {@link JdbcRateLimitKeyResolver} during a guarded statement. */
public final class JdbcRateLimitContext {
    private static final ThreadLocal<String> SELECTOR = new ThreadLocal<>();

    private JdbcRateLimitContext() {}

    static void enter(String selector) {
        SELECTOR.set(selector);
    }

    static void exit() {
        SELECTOR.remove();
    }

    public static String currentSelector() {
        return SELECTOR.get();
    }
}
