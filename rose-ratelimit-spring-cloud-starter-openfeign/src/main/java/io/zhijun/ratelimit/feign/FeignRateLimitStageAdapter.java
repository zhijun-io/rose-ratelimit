package io.zhijun.ratelimit.feign;

import feign.FeignException;
import feign.Request;
import feign.Response;
import io.zhijun.ratelimit.web.StageGuardDecision;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Builds Feign exceptions for guarded call rejections. */
final class FeignRateLimitStageAdapter {
    private FeignRateLimitStageAdapter() {}

    static FeignException toException(StageGuardDecision decision) {
        Request request = Request.create(
                Request.HttpMethod.GET, "rose://ratelimit", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(decision.status())
                .reason(decision.result())
                .request(request)
                .body(decision.body(), StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("rose-ratelimit", response);
    }
}
