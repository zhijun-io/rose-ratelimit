package io.zhijun.ratelimit.web;

/** Raised when {@link RoseResilience} blocks an invocation. */
public final class RateLimitInvokeRejectedException extends RuntimeException {
    private final int status;
    private final String result;

    public RateLimitInvokeRejectedException(int status, String body, String result) {
        super(body);
        this.status = status;
        this.result = result;
    }

    public int status() {
        return status;
    }

    public String result() {
        return result;
    }
}
