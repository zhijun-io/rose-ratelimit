package io.zhijun.ratelimit;

/** Optional v6 decorator chain around {@link RoseResilienceInvocation}. */
public interface ResiliencePipeline {
    RoseResilienceOutcome invoke(RoseResilienceRequest request);
}
