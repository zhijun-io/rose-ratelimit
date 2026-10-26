package io.zhijun.ratelimit.mq;

/** Thread-local MQ selector for {@link MqRateLimitKeyResolver} during a guarded consume. */
public final class MqRateLimitContext {
    private static final ThreadLocal<String> SELECTOR = new ThreadLocal<>();

    private MqRateLimitContext() {}

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
