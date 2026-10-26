package io.zhijun.ratelimit.restclient;

import java.time.Duration;

/** Raised before a RestClient request is sent when its rate-limit rule rejects it. */
public final class RateLimitRestClientRejectedException extends RuntimeException {
    private final int status;
    private final String body;
    private final String result;
    private final Duration retryAfter;

    public RateLimitRestClientRejectedException(int status, String body, String result, Duration retryAfter) {
        super(body);
        this.status = status;
        this.body = body;
        this.result = result;
        this.retryAfter = retryAfter;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }

    public String result() {
        return result;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
