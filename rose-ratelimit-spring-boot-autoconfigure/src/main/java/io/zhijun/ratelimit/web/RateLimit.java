package io.zhijun.ratelimit.web;

import java.lang.annotation.*;

/** References an externally configured MVC rule; method declarations override class declarations. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    String value();
}
