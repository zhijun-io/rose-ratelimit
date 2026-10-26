package io.zhijun.ratelimit.web;

/** Thread-local invoke selector for {@link InvokeRateLimitKeyResolver}. */
public final class InvokeRateLimitContext {
    private static final ThreadLocal<String> SELECTOR = new ThreadLocal<>();

    private InvokeRateLimitContext() {}

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
