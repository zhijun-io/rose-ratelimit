package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.*;
import io.zhijun.ratelimit.autoconfigure.RateLimitProperties.Rule;
import io.zhijun.ratelimit.web.*;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.util.pattern.PathPatternParser;

/** Validates configuration and compiles immutable runtime rules at startup. */
final class RateLimitRuleCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitRuleCompiler.class);
    private static final List<String> TARGET_PREFIXES =
            List.of("feign:", "rest-client:", "mybatis:", "jdbc:", "mq:", "invoke:");

    private RateLimitRuleCompiler() {}

    static RateLimitRules compile(
            RateLimitProperties properties,
            List<RateLimitPlugin> plugins,
            String applicationName,
            ClassLoader classLoader) {
        Map<String, RateLimitPlugin> registry = new HashMap<>();
        for (RateLimitPlugin plugin : plugins) {
            if (plugin.name() == null || !plugin.name().matches("[a-zA-Z0-9._-]+")) {
                throw new IllegalArgumentException("Invalid rate-limit plugin name");
            }
            if (registry.putIfAbsent(plugin.name(), plugin) != null) {
                throw new IllegalArgumentException("Duplicate rate-limit plugin: " + plugin.name());
            }
        }
        if (!(registry.get(properties.getStore().getType()) instanceof RateLimitStore)) {
            throw new IllegalArgumentException(
                    "Missing rate-limit store plugin '" + properties.getStore().getType()
                            + "'. For redis add rose-ratelimit-spring-boot-starter-redis and configure Lettuce.");
        }
        List<CompiledRule> compiled = new ArrayList<>();
        Map<String, CompiledRule> ids = new HashMap<>();
        for (int i = 0; i < properties.getRules().size(); i++) {
            Rule rule = properties.getRules().get(i);
            if (rule == null) throw new IllegalArgumentException("rose.ratelimit.rules[" + i + "] must not be null");
            try {
                if (rule.getId() == null || !rule.getId().matches("[a-zA-Z0-9._-]+")) {
                    throw new IllegalArgumentException(
                            "id must contain only letters, digits, dot, underscore or hyphen");
                }
                if (rule.getStage() == null || rule.getMissingKey() == null) {
                    throw new IllegalArgumentException("stage and missing-key are required");
                }
                validateStage(rule, properties, classLoader);
                if (rule.getStage() == Stage.FILTER
                        && (rule.getPath() == null || rule.getPath().isBlank())) {
                    throw new IllegalArgumentException("FILTER requires path");
                }
                if (rule.getPath() != null
                        && !rule.getPath().isBlank()
                        && !rule.getPath().startsWith("/")) {
                    throw new IllegalArgumentException("path must start with /");
                }
                if (rule.getStage() == Stage.FILTER
                        && (rule.getHandler() != null || rule.getScope() == Scope.HANDLER)) {
                    throw new IllegalArgumentException(
                            "handler and HANDLER scope require MVC, FEIGN, MYBATIS, JDBC, MQ, or INVOKE");
                }
                String keySourceName = rule.getKeySource();
                if ("ip".equals(keySourceName)) {
                    String stageKeySource = switch (rule.getStage()) {
                        case FEIGN -> "feign";
                        case MYBATIS -> "mybatis";
                        case JDBC -> "jdbc";
                        case MQ -> "mq";
                        case INVOKE -> "invoke";
                        default -> null;
                    };
                    if (stageKeySource != null) keySourceName = stageKeySource;
                }
                if (rule.getHandler() != null && rule.getHandler().isBlank()) {
                    throw new IllegalArgumentException("handler must not be blank");
                }
                ResilienceRuleKind kind = rule.getKind() == null ? ResilienceRuleKind.RATE_LIMIT : rule.getKind();
                if (kind == ResilienceRuleKind.COMPOSITE && rule.getBehaviors().isEmpty()) {
                    throw new IllegalArgumentException("composite kind requires non-empty behaviors");
                }
                requireBehaviorClass(
                        rule,
                        kind,
                        ResilienceBehavior.RETRY,
                        "retry",
                        "io.github.resilience4j.retry.Retry",
                        classLoader);
                requireBehaviorClass(
                        rule,
                        kind,
                        ResilienceBehavior.TIME_LIMITER,
                        "time-limiter",
                        "io.github.resilience4j.timelimiter.TimeLimiter",
                        classLoader);
                requireBehaviorClass(
                        rule,
                        kind,
                        ResilienceBehavior.BULKHEAD,
                        "bulkhead",
                        "io.github.resilience4j.bulkhead.Bulkhead",
                        classLoader);
                CircuitBreakerSpec circuitBreakerSpec = null;
                if (kind == ResilienceRuleKind.CIRCUIT_BREAKER
                        || (kind == ResilienceRuleKind.COMPOSITE
                                && rule.getBehaviors().contains(ResilienceBehavior.CIRCUIT_BREAKER))) {
                    circuitBreakerSpec = toCircuitBreakerSpec(rule.getCircuitBreaker());
                }
                RetrySpec retrySpec = null;
                if (kind == ResilienceRuleKind.COMPOSITE && rule.getBehaviors().contains(ResilienceBehavior.RETRY)) {
                    retrySpec = toRetrySpec(rule.getRetry());
                }
                TimeLimiterSpec timeLimiterSpec = null;
                if (kind == ResilienceRuleKind.COMPOSITE
                        && rule.getBehaviors().contains(ResilienceBehavior.TIME_LIMITER)) {
                    timeLimiterSpec = toTimeLimiterSpec(rule.getTimeLimiter());
                }
                BulkheadSpec bulkheadSpec = null;
                if (kind == ResilienceRuleKind.COMPOSITE && rule.getBehaviors().contains(ResilienceBehavior.BULKHEAD)) {
                    bulkheadSpec = toBulkheadSpec(rule.getBulkhead());
                }
                String storeName =
                        rule.getStore() == null ? properties.getStore().getType() : rule.getStore();
                if (!(registry.get(storeName) instanceof RateLimitStore store)) {
                    throw new IllegalArgumentException("Missing store plugin: " + storeName);
                }
                if (!(registry.get(keySourceName) instanceof RateLimitKeyResolver resolver)) {
                    throw new IllegalArgumentException("Missing key resolver plugin: " + keySourceName);
                }
                resolver.validate(rule.getKeyHeader());
                Set<String> methods = new LinkedHashSet<>();
                for (String method : rule.getMethods()) {
                    if (method == null || !method.matches("[A-Za-z]+"))
                        throw new IllegalArgumentException("Invalid HTTP method");
                    methods.add(method.toUpperCase(Locale.ROOT));
                }
                if (rule.getStatus() < 400 || rule.getStatus() > 599) {
                    throw new IllegalArgumentException("status must be an HTTP error status");
                }
                MediaType responseType = MediaType.parseMediaType(rule.getContentType());
                if (!responseType.isConcrete()) throw new IllegalArgumentException("content-type must be concrete");
                if (rule.getContentType().contains("\r")
                        || rule.getContentType().contains("\n")) {
                    throw new IllegalArgumentException("Invalid content-type");
                }
                String body = rule.getBody() == null
                        ? "{\"type\":\"about:blank\",\"title\":\"Rate limit exceeded\",\"status\":" + rule.getStatus()
                                + "}"
                        : rule.getBody();
                boolean stageBinding = isStageBinding(rule.getStage());
                String handlerPattern = stageBinding || rule.getStage() == Stage.REST_CLIENT
                        ? stageBindingPattern(rule)
                        : rule.getHandler();
                Scope scope = rule.getScope() == null
                        ? (rule.getStage() == Stage.MVC || stageBinding ? Scope.HANDLER : Scope.RULE)
                        : rule.getScope();
                CompiledRule result = new CompiledRule(
                        rule.getId(),
                        rule.isEnabled(),
                        rule.getStage(),
                        stageBinding || rule.getPath() == null ? null : new PathPatternParser().parse(rule.getPath()),
                        stageBinding ? Set.of() : Set.copyOf(methods),
                        handlerPattern,
                        scope,
                        new RateLimitPolicy(rule.getCapacity(), rule.getRefillTokens(), rule.getRefillPeriod()),
                        store,
                        resolver,
                        rule.getKeyHeader(),
                        rule.getMissingKey(),
                        rule.getFailureMode() == null
                                ? Objects.requireNonNull(properties.getFailureMode())
                                : rule.getFailureMode(),
                        rule.getStatus(),
                        rule.getContentType(),
                        body,
                        kind,
                        circuitBreakerSpec,
                        rule.getCircuitOpenStatus(),
                        retrySpec,
                        timeLimiterSpec,
                        bulkheadSpec);
                if (ids.putIfAbsent(result.id(), result) != null) throw new IllegalArgumentException("Duplicate id");
                compiled.add(result);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "rose.ratelimit.rules[" + i + "] (" + rule.getId() + "): " + e.getMessage(), e);
            }
        }
        boolean redis = properties.getStore().getType().equals("redis")
                || compiled.stream().anyMatch(r -> r.store().name().equals("redis"));
        String resolved = properties.getNamespace() == null ? applicationName : properties.getNamespace();
        if (resolved == null || resolved.isBlank()) {
            if (redis)
                throw new IllegalArgumentException(
                        "Redis requires rose.ratelimit.namespace or spring.application.name");
            resolved = "application";
        }
        warnHttpBusinessDecoration(compiled);
        return new RateLimitRules(resolved, compiled);
    }

    private static void validateStage(Rule rule, RateLimitProperties properties, ClassLoader classLoader) {
        Stage stage = rule.getStage();
        if (rule.isEnabled()) {
            switch (stage) {
                case MVC ->
                    requireClass(
                            properties.isMvcEnabled(),
                            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
                            "MVC rules require Spring MVC on the classpath",
                            classLoader);
                case FEIGN ->
                    requireClass(
                            properties.getFeign().isEnabled(),
                            "org.springframework.cloud.openfeign.FeignClient",
                            "FEIGN rules require OpenFeign and rose-ratelimit-spring-cloud-starter-openfeign on the classpath",
                            classLoader);
                case REST_CLIENT ->
                    requireClass(
                            properties.getRestClient().isEnabled(),
                            "io.zhijun.ratelimit.restclient.RateLimitRestClientAutoConfiguration",
                            "REST_CLIENT rules require rose-ratelimit-spring-boot-starter-restclient on the classpath",
                            classLoader);
                case MYBATIS ->
                    requireClass(
                            properties.getMybatis().isEnabled(),
                            "org.apache.ibatis.session.SqlSessionFactory",
                            "MYBATIS rules require MyBatis and rose-ratelimit-spring-boot-starter-mybatis on the classpath",
                            classLoader);
                case JDBC ->
                    requireClass(
                            properties.getJdbc().isEnabled(),
                            "com.alibaba.druid.pool.DruidDataSource",
                            "JDBC rules require Alibaba Druid and rose-ratelimit-spring-boot-starter-alibaba-druid on the classpath",
                            classLoader);
                case MQ -> {
                    if (properties.getMq().isEnabled()) {
                        validateMqTransport(rule, classLoader);
                    }
                }
                default -> {}
            }
        }

        switch (stage) {
            case FEIGN, MYBATIS, MQ, INVOKE -> validateStageBindingRule(rule, stage.name());
            case REST_CLIENT -> validateRestClientRule(rule);
            case JDBC -> {
                validateStageBindingRule(rule, stage.name());
                validateJdbcHandler(stageBindingPattern(rule));
            }
            default -> {}
        }
    }

    private static void requireClass(boolean enabled, String className, String message, ClassLoader classLoader) {
        if (enabled && !org.springframework.util.ClassUtils.isPresent(className, classLoader)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireBehaviorClass(
            Rule rule,
            ResilienceRuleKind kind,
            ResilienceBehavior behavior,
            String name,
            String className,
            ClassLoader classLoader) {
        if (rule.isEnabled()
                && kind == ResilienceRuleKind.COMPOSITE
                && rule.getBehaviors().contains(behavior)
                && !org.springframework.util.ClassUtils.isPresent(className, classLoader)) {
            throw new IllegalArgumentException(
                    name + " behavior requires rose-ratelimit-spring-boot-starter-resilience4j on the classpath");
        }
    }

    private static void warnHttpBusinessDecoration(List<CompiledRule> compiled) {
        for (CompiledRule rule : compiled) {
            if (!rule.enabled() || rule.stage() != Stage.MVC) {
                continue;
            }
            if (!rule.usesBusinessDecoration()) {
                continue;
            }
            LOG.warn(
                    "Rule '{}': retry/time-limiter/bulkhead on stage MVC does not decorate the controller; "
                            + "use stage FILTER with the same path, or stage INVOKE with RoseResilience",
                    rule.id());
        }
    }

    private static void validateJdbcHandler(String pattern) {
        if (pattern != null && !pattern.isBlank() && !pattern.startsWith("sql-table:")) {
            throw new IllegalArgumentException("JDBC target must use jdbc:sql-table:... prefix");
        }
    }

    private static void validateMqTransport(Rule rule, ClassLoader classLoader) {
        String pattern = stageBindingPattern(rule);
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        if (pattern.startsWith("kafka:")) {
            if (!org.springframework.util.ClassUtils.isPresent(
                    "org.springframework.kafka.annotation.KafkaListener", classLoader)) {
                throw new IllegalArgumentException(
                        "MQ rules with mq:kafka: target require Spring Kafka, rose-ratelimit-spring-boot-starter-mq, and spring-kafka on the classpath");
            }
            return;
        }
        if (pattern.startsWith("rabbit:")) {
            if (!org.springframework.util.ClassUtils.isPresent(
                    "org.springframework.amqp.rabbit.annotation.RabbitListener", classLoader)) {
                throw new IllegalArgumentException(
                        "MQ rules with mq:rabbit: target require Spring AMQP, rose-ratelimit-spring-boot-starter-mq, and spring-boot-amqp on the classpath");
            }
            return;
        }
        throw new IllegalArgumentException(
                "MQ target must use mq:kafka:... or mq:rabbit:... (compiled selector kafka:... or rabbit:...)");
    }

    private static void validateStageBindingRule(Rule rule, String stageName) {
        String pattern = stageBindingPattern(rule);
        if (rule.isEnabled() && (pattern == null || pattern.isBlank())) {
            throw new IllegalArgumentException(stageName + " requires target or handler");
        }
        if (rule.getPath() != null && !rule.getPath().isBlank()) {
            throw new IllegalArgumentException(stageName + " must not use path");
        }
        if (!rule.getMethods().isEmpty()) {
            throw new IllegalArgumentException(stageName + " must not use HTTP methods");
        }
    }

    private static void validateRestClientRule(Rule rule) {
        String target = rule.getTarget();
        if (target != null && !target.isBlank() && !target.startsWith("rest-client:")) {
            throw new IllegalArgumentException("REST_CLIENT target must use rest-client:<origin-pattern> prefix");
        }
        if (rule.isEnabled()
                && (target == null || target.isBlank())
                && (rule.getHandler() == null || rule.getHandler().isBlank())
                && (rule.getPath() == null || rule.getPath().isBlank())) {
            throw new IllegalArgumentException("REST_CLIENT requires target, handler, or path");
        }
        if (target != null
                && target.startsWith("rest-client:")
                && target.substring("rest-client:".length()).isBlank()) {
            throw new IllegalArgumentException("REST_CLIENT target origin pattern must not be blank");
        }
    }

    private static boolean isStageBinding(Stage stage) {
        return stage == Stage.FEIGN
                || stage == Stage.MYBATIS
                || stage == Stage.JDBC
                || stage == Stage.MQ
                || stage == Stage.INVOKE;
    }

    private static String stageBindingPattern(Rule rule) {
        String raw = rule.getTarget();
        if (raw == null || raw.isBlank()) {
            raw = rule.getHandler();
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (String prefix : TARGET_PREFIXES) {
            if (raw.startsWith(prefix)) {
                return raw.substring(prefix.length());
            }
        }
        return raw;
    }

    private static RetrySpec toRetrySpec(RateLimitProperties.Retry retry) {
        return new RetrySpec(retry.getMaxAttempts(), retry.getWaitDuration());
    }

    private static TimeLimiterSpec toTimeLimiterSpec(RateLimitProperties.TimeLimiter timeLimiter) {
        return new TimeLimiterSpec(timeLimiter.getTimeoutDuration(), timeLimiter.isCancelRunningFuture());
    }

    private static BulkheadSpec toBulkheadSpec(RateLimitProperties.Bulkhead bulkhead) {
        return new BulkheadSpec(bulkhead.getMaxConcurrentCalls(), bulkhead.getMaxWaitDuration());
    }

    private static CircuitBreakerSpec toCircuitBreakerSpec(RateLimitProperties.CircuitBreaker circuitBreaker) {
        return new CircuitBreakerSpec(
                circuitBreaker.getFailureRateThreshold(),
                circuitBreaker.getSlowCallRateThreshold(),
                circuitBreaker.getSlowCallDurationThreshold(),
                circuitBreaker.getMinimumNumberOfCalls(),
                circuitBreaker.getSlidingWindowSize(),
                circuitBreaker.getWaitDurationInOpenState(),
                circuitBreaker.getPermittedNumberOfCallsInHalfOpenState());
    }
}
