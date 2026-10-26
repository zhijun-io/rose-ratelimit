package io.zhijun.ratelimit;

import java.util.Optional;

/** Rule-id keyed pipelines supplied by optional engine modules (e.g. Resilience4j). */
public interface ResiliencePipelineRegistry {
    Optional<ResiliencePipeline> pipelineFor(String ruleId);
}
