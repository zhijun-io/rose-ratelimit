package io.zhijun.ratelimit.feign;

/** Thread-local identity for {@link FeignRateLimitKeyResolver} during a guarded Feign call. */
public final class FeignRateLimitContext {
    private static final ThreadLocal<String> SELECTOR = new ThreadLocal<>();

    private FeignRateLimitContext() {}

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
