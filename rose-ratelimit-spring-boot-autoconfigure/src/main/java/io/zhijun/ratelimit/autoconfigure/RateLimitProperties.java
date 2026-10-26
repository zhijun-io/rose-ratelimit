package io.zhijun.ratelimit.autoconfigure;

import io.zhijun.ratelimit.ResilienceBehavior;
import io.zhijun.ratelimit.ResilienceRuleKind;
import io.zhijun.ratelimit.web.RateLimitOptions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rose.ratelimit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean filterEnabled = true;
    private boolean mvcEnabled = true;
    private int filterOrder = Integer.MIN_VALUE + 10;
    private String namespace = null;
    private FailureMode failureMode = FailureMode.OPEN;
    private Store store = new Store();
    private Local local = new Local();
    private Mvc mvc = new Mvc();
    private RestClient restClient = new RestClient();
    private Feign feign = new Feign();
    private Mybatis mybatis = new Mybatis();
    private Jdbc jdbc = new Jdbc();
    private Mq mq = new Mq();
    private Invoke invoke = new Invoke();
    private List<Rule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    public void setFilterEnabled(boolean filterEnabled) {
        this.filterEnabled = filterEnabled;
    }

    public boolean isMvcEnabled() {
        return mvcEnabled;
    }

    public void setMvcEnabled(boolean mvcEnabled) {
        this.mvcEnabled = mvcEnabled;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public FailureMode getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(FailureMode failureMode) {
        this.failureMode = failureMode;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public Mvc getMvc() {
        return mvc;
    }

    public void setMvc(Mvc mvc) {
        this.mvc = mvc;
    }

    public Feign getFeign() {
        return feign;
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void setFeign(Feign feign) {
        this.feign = feign;
    }

    public Mybatis getMybatis() {
        return mybatis;
    }

    public void setMybatis(Mybatis mybatis) {
        this.mybatis = mybatis;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }

    public void setJdbc(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public Mq getMq() {
        return mq;
    }

    public void setMq(Mq mq) {
        this.mq = mq;
    }

    public Invoke getInvoke() {
        return invoke;
    }

    public void setInvoke(Invoke invoke) {
        this.invoke = invoke;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class Rule {
        private String id = null;
        private boolean enabled = true;
        private Stage stage = Stage.FILTER;
        private Scope scope = null;
        private String path = null;
        private String handler = null;
        private String target = null;
        private List<String> methods = new ArrayList<>();
        private long capacity = 10;
        private long refillTokens = 10;
        private Duration refillPeriod = Duration.ofSeconds(1);
        private String keySource = "ip";
        private String keyHeader = null;
        private MissingKey missingKey = MissingKey.REJECT;
        private FailureMode failureMode = null;
        private String store = null;
        private int status = 429;
        private String contentType = "application/problem+json";
        private String body = null;
        private ResilienceRuleKind kind = ResilienceRuleKind.RATE_LIMIT;
        private List<ResilienceBehavior> behaviors = new ArrayList<>();
        private CircuitBreaker circuitBreaker = new CircuitBreaker();
        private Retry retry = new Retry();
        private TimeLimiter timeLimiter = new TimeLimiter();
        private Bulkhead bulkhead = new Bulkhead();
        private int circuitOpenStatus = 503;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Stage getStage() {
            return stage;
        }

        public void setStage(Stage stage) {
            this.stage = stage;
        }

        public Scope getScope() {
            return scope;
        }

        public void setScope(Scope scope) {
            this.scope = scope;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getHandler() {
            return handler;
        }

        public void setHandler(String handler) {
            this.handler = handler;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        public String getKeySource() {
            return keySource;
        }

        public void setKeySource(String keySource) {
            this.keySource = keySource;
        }

        public String getKeyHeader() {
            return keyHeader;
        }

        public void setKeyHeader(String keyHeader) {
            this.keyHeader = keyHeader;
        }

        public MissingKey getMissingKey() {
            return missingKey;
        }

        public void setMissingKey(MissingKey missingKey) {
            this.missingKey = missingKey;
        }

        public FailureMode getFailureMode() {
            return failureMode;
        }

        public void setFailureMode(FailureMode failureMode) {
            this.failureMode = failureMode;
        }

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public ResilienceRuleKind getKind() {
            return kind;
        }

        public void setKind(ResilienceRuleKind kind) {
            this.kind = kind;
        }

        public List<ResilienceBehavior> getBehaviors() {
            return behaviors;
        }

        public void setBehaviors(List<ResilienceBehavior> behaviors) {
            this.behaviors = behaviors;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public Retry getRetry() {
            return retry;
        }

        public void setRetry(Retry retry) {
            this.retry = retry;
        }

        public TimeLimiter getTimeLimiter() {
            return timeLimiter;
        }

        public void setTimeLimiter(TimeLimiter timeLimiter) {
            this.timeLimiter = timeLimiter;
        }

        public Bulkhead getBulkhead() {
            return bulkhead;
        }

        public void setBulkhead(Bulkhead bulkhead) {
            this.bulkhead = bulkhead;
        }

        public int getCircuitOpenStatus() {
            return circuitOpenStatus;
        }

        public void setCircuitOpenStatus(int circuitOpenStatus) {
            this.circuitOpenStatus = circuitOpenStatus;
        }
    }

    public static class Bulkhead {
        private int maxConcurrentCalls = 25;
        private Duration maxWaitDuration = Duration.ZERO;

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public Duration getMaxWaitDuration() {
            return maxWaitDuration;
        }

        public void setMaxWaitDuration(Duration maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }
    }

    public static class TimeLimiter {
        private Duration timeoutDuration = Duration.ofSeconds(5);
        private boolean cancelRunningFuture = true;

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        public boolean isCancelRunningFuture() {
            return cancelRunningFuture;
        }

        public void setCancelRunningFuture(boolean cancelRunningFuture) {
            this.cancelRunningFuture = cancelRunningFuture;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }
    }

    public static class CircuitBreaker {
        private float failureRateThreshold = 50;
        private float slowCallRateThreshold = 100;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(2);
        private int minimumNumberOfCalls = 10;
        private int slidingWindowSize = 20;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int permittedNumberOfCallsInHalfOpenState = 5;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }
    }

    public static class Store {
        private String type = "local";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Feign {
        private boolean enabled = true;
        private boolean requireClientMatch = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireClientMatch() {
            return requireClientMatch;
        }

        public void setRequireClientMatch(boolean requireClientMatch) {
            this.requireClientMatch = requireClientMatch;
        }
    }

    public static class RestClient {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Jdbc {
        private boolean enabled = true;
        private boolean requireDatasourceMatch = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireDatasourceMatch() {
            return requireDatasourceMatch;
        }

        public void setRequireDatasourceMatch(boolean requireDatasourceMatch) {
            this.requireDatasourceMatch = requireDatasourceMatch;
        }
    }

    public static class Mybatis {
        private boolean enabled = true;
        private boolean requireStatementMatch = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireStatementMatch() {
            return requireStatementMatch;
        }

        public void setRequireStatementMatch(boolean requireStatementMatch) {
            this.requireStatementMatch = requireStatementMatch;
        }
    }

    public static class Mq {
        private boolean enabled = true;
        private boolean requireListenerMatch = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireListenerMatch() {
            return requireListenerMatch;
        }

        public void setRequireListenerMatch(boolean requireListenerMatch) {
            this.requireListenerMatch = requireListenerMatch;
        }
    }

    public static class Invoke {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Mvc {
        private boolean requireHandlerMatch = true;

        public boolean isRequireHandlerMatch() {
            return requireHandlerMatch;
        }

        public void setRequireHandlerMatch(boolean requireHandlerMatch) {
            this.requireHandlerMatch = requireHandlerMatch;
        }
    }

    public static class Local {
        private int maxBuckets = 100_000;

        public int getMaxBuckets() {
            return maxBuckets;
        }

        public void setMaxBuckets(int maxBuckets) {
            this.maxBuckets = maxBuckets;
        }
    }
}
