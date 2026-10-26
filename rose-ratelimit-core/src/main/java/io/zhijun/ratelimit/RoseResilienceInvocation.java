package io.zhijun.ratelimit;

/** v6 core: rate-limit path only; Resilience4j decorators attach in later milestones. */
public final class RoseResilienceInvocation {

    public RoseResilienceOutcome invoke(RoseResilienceRequest request) {
        boolean missing =
                request.resolvedIdentity() == null || request.resolvedIdentity().isBlank();
        if (missing && request.missingKeyPolicy() == RoseResilienceRequest.MissingKeyPolicy.REJECT) {
            return RoseResilienceOutcome.missingIdentity();
        }
        try {
            RateLimitDecision decision = request.store().tryAcquire(request.storageKey(), request.policy());
            return decision.allowed()
                    ? RoseResilienceOutcome.allowed()
                    : RoseResilienceOutcome.rejected(decision.retryAfter());
        } catch (RateLimitBackendException e) {
            return request.failureMode() == RoseResilienceRequest.FailureMode.OPEN
                    ? RoseResilienceOutcome.backendOpen()
                    : RoseResilienceOutcome.backendClosed();
        }
    }
}
