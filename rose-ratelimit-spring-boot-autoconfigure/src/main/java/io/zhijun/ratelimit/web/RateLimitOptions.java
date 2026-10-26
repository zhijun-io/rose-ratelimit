package io.zhijun.ratelimit.web;

/** Public rule options shared by configuration and request execution. */
public final class RateLimitOptions {
    private RateLimitOptions() {}

    public enum FailureMode {
        OPEN,
        CLOSED
    }

    public enum Stage {
        FILTER,
        MVC,
        REST_CLIENT,
        FEIGN,
        MYBATIS,
        JDBC,
        MQ,
        INVOKE
    }

    public enum Scope {
        RULE,
        HANDLER
    }

    public enum MissingKey {
        REJECT,
        SHARED
    }
}
