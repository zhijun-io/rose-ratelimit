package io.zhijun.ratelimit;

public final class RateLimitBackendException extends RuntimeException {
    public RateLimitBackendException(String message) {
        super(message);
    }

    public RateLimitBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
