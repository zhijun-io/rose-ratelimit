package io.zhijun.ratelimit;

/** Observations contain bounded configuration labels, never client identities. */
@FunctionalInterface
public interface RateLimitObserver {
    void onDecision(String ruleId, String backend, String result);
}
