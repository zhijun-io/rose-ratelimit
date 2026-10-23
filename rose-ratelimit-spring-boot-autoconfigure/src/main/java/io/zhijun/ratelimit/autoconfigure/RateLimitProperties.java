package io.zhijun.ratelimit.autoconfigure;

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
