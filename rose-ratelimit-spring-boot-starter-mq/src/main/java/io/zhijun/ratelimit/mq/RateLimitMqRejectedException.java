package io.zhijun.ratelimit.mq;

/** Raised when an MQ consumer is blocked by rate-limit or circuit-breaker policy. */
public final class RateLimitMqRejectedException extends RuntimeException {
    private final int status;
    private final String result;

    public RateLimitMqRejectedException(int status, String body, String result) {
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
