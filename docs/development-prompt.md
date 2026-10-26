# Rose Rate Limit：开发与架构说明

本文记录当前实现与设计依据，面向继续开发、重构与 Code Review。[README](../README.md) 面向使用者；行为变更需同步两处文档。

**技术基线**：Spring Boot 4.1.2、Java 25。**最后对照仓库**：2026-10-26。当前交付能力见 §16.10。

**架构与模块清单**：见 §3、§16。

本文记录 v5 HTTP 入口与 v6 多阶段架构的设计依据。限流额度由 Bucket4j Store 执行；Resilience4j 提供熔断和业务调用装饰。迁移映射见 [migration-v6.md](migration-v6.md)。

---

## 1. 目标与非目标

### 1.1 目标

- 引入依赖并配置 `rose.ratelimit.rules` 即可限流，**无需**业务 `@Enable`、业务 API 调用。
- 覆盖 Servlet Filter、Spring MVC，以及可选的 RestClient、OpenFeign、MyBatis、Druid JDBC、Kafka/Rabbit MQ 和编程式 INVOKE 阶段。
- 可选 `@RateLimit("rule-id")` 显式引用已存在的 MVC 规则。
- **Token Bucket**：每次匹配的调用消耗 1 token；HTTP 拒绝时非阻塞返回可配置错误响应，默认 **429** 与向上取整的 **Retry-After**。
- 本机 bounded 存储与 **Redis 分布式**存储；策略变更通过 **fingerprint** 隔离桶状态。
- 技术栈：**Spring Boot 4.1.2**、**Java 25**、Maven。

### 1.2 非目标（首版及近期均不做）

- 不支持 WebFlux、规则热刷新、请求体账号解析和业务配额事务。
- 配置项 `algorithm:` 或多算法 DSL；换引擎通过 **Maven 模块 + `RateLimitStore` Bean** 完成。
- Resilience4j 式阻塞 `waitForPermission` 作为默认 HTTP 拒绝路径。
- 扫描/迁移历史 Redis Lua 键；跨桶事务或「后续拒绝退还已扣额度」。
- 运行期动态注册 `@RequestMapping` 后 **自动** 更新 MVC 绑定表（须 **重启** 应用，见 §4.4）。

---

## 2. 设计原则（保留两边优点）

| 原则 | 来源 | 说明 |
| --- | --- | --- |
| 配置即限流、规则列表 | Rose | `rules[]` 含 id、stage、path、额度、身份、故障模式 |
| Filter 先于认证可限登录 | Rose | `filter-order`、FILTER stage |
| 共同执行核心 | Rose | `RateLimitGuardedInvocation` 统一决策/观测/装饰；HTTP 由 `RateLimitExecutor` 去重并写响应 |
| 启动期编译不可变规则 | Rose | 运行时不用可变 `ConfigurationProperties` |
| 映射就绪后绑定 MVC | Microsphere | Handler 与规则预关联；配置错误启动失败 |
| FILTER 路径与容器派发一致 | Microsphere 思路 + 安全 | 不用 raw `getRequestURI()` 单独匹配 |
| 无 Enable 的 AutoConfiguration | Rose | `AutoConfiguration.imports` + 条件 Bean |
| 具名 Store / Resolver 插件 | Rose | `RateLimitPlugin.name()`；冲突与缺失启动失败 |
| Store 引擎可插拔 | Rose 演进 | core 不依赖 Bucket4j；默认 bucket4j 模块 |
| 发布 BOM 对齐版本 | Microsphere | `rose-ratelimit-bom` 供外部 import；Reactor 内不 import 自身 BOM |

---

## 3. 模块与依赖

### 3.1 Reactor 模块

根工程：`io.zhijun:rose-ratelimit`（packaging `pom`，继承 `spring-boot-starter-parent` 4.1.2）。`bucket4j.version` 定义在根 POM `properties`，BOM 与根 POM 的 `dependencyManagement` 共用该属性。

| 目录 / artifactId | packaging | 发布 | 职责 |
| --- | --- | --- | --- |
| `rose-ratelimit-bom` | pom | **是** | 对外 BOM；管理本仓库构件 + Bucket4j 坐标 |
| `rose-ratelimit-core` | jar | 是 | 策略、决策与 `RateLimitStore` SPI；不依赖 Bucket4j |
| `rose-ratelimit-bucket4j` | jar | 是 | `LocalRateLimitStore` + 默认本机自动配置 |
| `rose-ratelimit-spring-boot-autoconfigure` | jar | 是 | 配置、规则编译、Servlet/MVC 入口与共享执行流程 |
| `rose-ratelimit-spring-boot-starter` | jar | 是 | 默认 HTTP Starter（autoconfigure + bucket4j） |
| `rose-ratelimit-spring-boot-starter-redis` | jar | 是 | Redis `RateLimitStore`（Bucket4j + Lettuce） |
| `rose-ratelimit-spring-boot-starter-restclient` | jar | 是 | 同步 `RestClient` 出站 HTTP 限流 |
| `rose-ratelimit-spring-boot-starter-resilience4j` | jar | 是 | Resilience4j 熔断、重试、超时和舱壁 |
| `rose-ratelimit-spring-cloud-starter-openfeign` | jar | 是 | Spring Cloud OpenFeign 集成 |
| `rose-ratelimit-spring-boot-starter-mybatis` | jar | 是 | MyBatis mapped statement 集成 |
| `rose-ratelimit-spring-boot-starter-alibaba-druid` | jar | 是 | Alibaba Druid JDBC 集成 |
| `rose-ratelimit-spring-boot-starter-mq` | jar | 是 | Kafka / Rabbit listener 集成 |
| `rose-ratelimit-sample` | jar | 否 | 示例应用，不发布 |
| `rose-ratelimit-coverage` | pom | 否 | 聚合 JaCoCo 报告并检查总行覆盖率 |

**模块顺序**：Reactor 中 **BOM 模块置于首位**（`pom.xml` `<modules>`），便于发布与人工对照。

### 3.2 BOM（`rose-ratelimit-bom`）

**用途**：应用侧在 `dependencyManagement` 中 import，统一 `io.zhijun` 构件与 Bucket4j 版本；与 [microsphere-resilience4j-dependencies](https://github.com/microsphere-projects/microsphere-resilience4j) 角色相同，artifact 命名为 **`…-bom`**。

**当前 BOM 管理的 Rose 构件**：

- `rose-ratelimit-core`
- `rose-ratelimit-bucket4j`
- `rose-ratelimit-spring-boot-autoconfigure`
- `rose-ratelimit-spring-boot-starter`
- `rose-ratelimit-spring-boot-starter-redis`
- `rose-ratelimit-spring-boot-starter-restclient`
- `rose-ratelimit-spring-boot-starter-mq`
- `rose-ratelimit-spring-boot-starter-resilience4j`
- `rose-ratelimit-spring-cloud-starter-openfeign`
- `rose-ratelimit-spring-boot-starter-mybatis`
- `rose-ratelimit-spring-boot-starter-alibaba-druid`
- `com.bucket4j:bucket4j_jdk17-core`
- `com.bucket4j:bucket4j_jdk17-lettuce`

示例和覆盖率模块不发布，也不加入 BOM。

**外部用法**（与 README 一致）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.zhijun</groupId>
            <artifactId>rose-ratelimit-bom</artifactId>
            <version>${rose-ratelimit.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Reactor 内为何不能 `import` 自身 BOM**：BOM 子模块继承根工程；若根 POM 再 `import` 同一 BOM，Maven 会在解析 BOM 模块时形成 **import 循环**。因此：

- **构建 Reactor**：父 POM **内联**与 BOM **相同内容**的 `dependencyManagement`。
- **发布消费**：仅发布 `rose-ratelimit-bom` 供外部 import；版本须与当次 release 的 `${project.version}` 一致。

**维护规则**（新增/升级可发布模块或第三方引擎坐标时 **必须** 同时改两处）：

1. 根 `pom.xml` 的 `dependencyManagement`
2. `rose-ratelimit-bom/pom.xml` `dependencyManagement`

发布前人工比对两处 `dependencyManagement` 条目，确保构件集合与版本保持一致。

core 不依赖 Bucket4j；默认本机存储由独立的 `rose-ratelimit-bucket4j` 模块提供。公共 API 不暴露 `io.github.bucket4j.*` 类型。

### 3.3 执行路径

```text
application.yml / @RateLimit
  → RateLimitRuleCompiler（启动校验 + CompiledRule + MVC 绑定表）
  → RateLimitFilter / RateLimitInterceptor（选规则）
  → RateLimitExecutor / RateLimitGuardedInvocation（身份、scope、去重、故障策略、观测）
  → 可选 ResiliencePipelineRegistry 或 RoseResilienceInvocation
  → RateLimitStore.tryAcquire(storageKey, policy)
```

**core** 不依赖 Servlet/Spring MVC。`RateLimitKeyResolver` 位于 autoconfigure 的 `web` 包。

---

## 4. 规则模型与匹配

### 4.1 规则字段（与 README 一致）

- **id**：必填、唯一，`[a-zA-Z0-9._-]+`
- **stage**：`FILTER`（默认）、`MVC`、`REST_CLIENT`、`FEIGN`、`MYBATIS`、`JDBC`、`MQ` 或 `INVOKE`
- **path**：**应用内路径**（context path 之后，与 §4.3 `pathWithinApplication` 一致）；Spring **PathPattern**；非法模式启动失败
- **methods**：空表示全部 HTTP 方法
- **handler**（MVC）：`类全名#方法名`，支持 `*`（与 `PatternMatchUtils.simpleMatch` 一致）
- **scope**：FILTER 默认 `RULE`；MVC 默认 `HANDLER`
- **capacity / refill-tokens / refill-period**：默认 10/10/1s；周期 ≥ 1ms
- **key-source / key-header / missing-key**
- **store**：继承 `rose.ratelimit.store.type`；默认 `local`，Redis 插件名为 `redis`；缺少对应 `RateLimitStore` 时启动失败
- **failure-mode**：继承全局，默认 **OPEN**；保护性规则建议 **CLOSED**
- **status / content-type / body**：默认 429、problem+json、自动生成体

**全局开关（补充）**：

| 属性 | 默认 | 含义 |
| --- | --- | --- |
| `rose.ratelimit.mvc.require-handler-match` | `true` | 见 §4.4；`false` 时 MVC 外部规则回退运行时 `select`，且 **N=0 不导致启动失败**（打 WARN） |

### 4.2 HTTP 阶段语义

1. 每阶段按 **配置顺序** 第一条匹配的 **enabled** 规则执行；无匹配 **放行**。
2. FILTER **必须** path；MVC 支持 path、methods、handler。
3. 已启用 MVC 规则且 classpath 无 MVC → **启动失败**（`mvc-enabled=false` 除外）。
4. FILTER 不允许 handler / `HANDLER` scope。
5. MVC **无 path 且无 handler** 的规则 **仅供** `@RateLimit` 引用。
6. `@RateLimit` 类或方法，方法覆盖类；映射上的注解必须引用存在的 **MVC** 规则。
7. MVC：**外部匹配规则先执行**，**注解规则后**；不同 id **叠加**；同 id **同请求一次**。
8. 注解绑定后 **不使用** 该规则的 path/methods/handler 选择条件；额度、key、故障、响应仍有效。
9. 注解 **不能撤销** 已执行的 FILTER 规则。
10. 请求属性记录 outcome；**异步重派** 同 id 不重复扣减；**错误分派** 不重跑入口 Filter（`OncePerRequestFilter` 语义）。
11. 多条规则 **非跨桶事务**；后续拒绝 **不退还** 已扣额度。
12. `enabled` / `filter-enabled` / `mvc-enabled` 分级关闭。

**区分**：§4.2-1 的「无匹配放行」指 **运行时** 该请求/Handler 未命中任何规则；§4.4 的「N=0 启动失败」指 **配置声称** 保护某 path/handler，但映射表上 **不存在** 任何 HandlerMethod（配置错误）。

### 4.3 FILTER 路径匹配（必须实现）

- 用于 PathPattern 的路径必须是 **应用内映射路径**，与容器/Spring 派发一致：优先 `ServletRequestPathUtils.parse(request).pathWithinApplication()`（已剥离 context path；含 Boot 4.x 对 path-mapped servlet 的 servletPath 前缀处理）。
- **禁止** 仅使用 `getRequestURI().substring(contextPath.length())` 做安全相关匹配。
- **必须** 使 `/api/auth/./login`、`/api/auth/login/../login` 等与 `/api/auth/login` 在 FILTER 阶段命中同一规则，**前提** 是容器未以 400 拒绝该请求（Tomcat 等对 `//`、`/..` 规范化后仍进入 Filter 链）。
- **`%2e%2e` 等编码穿越**：若容器解码/规范化后映射路径等价于受保护 path，须命中规则；若容器直接 **400**，限流不负责「拒绝攻击请求」，只保证 **能进入业务的请求** 与 URL 映射一致。

### 4.4 MVC 匹配（启动绑定 + 运行时查表）

#### 4.4.1 绑定时机

- 在 **`SmartInitializingSingleton.afterSingletonsInstantiated`** 同一阶段（与现有 `@RateLimit` 映射校验一致），遍历 `ObjectProvider<RequestMappingHandlerMapping>.orderedStream()`，合并全部 `getHandlerMethods()`。
- **不** 监听运行期新增映射；Actuator/springdoc 等若晚于该阶段注册映射，须 **重启** 后绑定表才更新（写入 README 限制）。

#### 4.4.2 绑定算法（与运行时 `select` 等价）

对每个合并得到的 `HandlerMethod`：

1. 构造 **selector** = `beanType.getName() + "#" + method.getName()`（与现 `RateLimitInterceptor` 一致）。
2. 对该 Method 的 **每条** `RequestMappingInfo`（多路径、多 method 均需覆盖）：
   - 取映射 path 与 HTTP method；
   - 用 **合成请求** 或等价逻辑调用与运行时相同的匹配函数：在 **enabled、stage=MVC、且非「仅注解」** 的规则列表中，按 **配置顺序** 找第一条满足 path + methods + handler 的规则。
3. 若某条 `(HandlerMethod, 映射 path, HTTP method)` 命中规则 `R`，按该 HandlerMethod 的 selector 写入索引；多条映射命中不同规则时保留配置顺序最靠前的规则。一个 selector 只关联一条外部规则。

**实现提示**：可简化为对每个 `HandlerMethod` 固定一组代表 path/method（例如第一条映射），但必须证明与运行时 `select(request, MVC, selector)` 对 **实际会派发到该 Method 的请求** 结果一致；集成测试须覆盖「类级 + 方法级 `@RequestMapping`」「多 path」。

**索引键**：selector 字符串 `beanType.getName() + "#" + method.getName()`，与运行时 `preHandle` 生成的值相同。

**实现说明（`RateLimitMvcBinder`，包 `io.zhijun.ratelimit.autoconfigure` 或 `web`）**：

```text
for (RequestMappingHandlerMapping mapping : mappings.orderedStream()) {
  for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
    RequestMappingInfo info = entry.getKey();
    HandlerMethod handlerMethod = entry.getValue();
    String selector = handlerMethod.getBeanType().getName() + "#"
        + handlerMethod.getMethod().getName();

    Set<String> paths = info.getPathPatternsCondition().getPatternValues();
    if (paths.isEmpty()) paths = Set.of("");

    Set<RequestMethod> requestMethods = info.getMethodsCondition().getMethods();
    boolean allMethods = requestMethods.isEmpty();

    CompiledRule chosen = null;
    for (String path : paths) {
      Iterable<RequestMethod> methods = allMethods
          ? ALL_STANDARD_METHODS   // GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE
          : requestMethods;
      for (RequestMethod rm : methods) {
        HttpServletRequest synthetic = syntheticRequest(path, rm.name(), handlerMethod);
        CompiledRule hit = rules.select(synthetic, Stage.MVC, selector);
        if (hit != null) {
          chosen = earlierInConfigOrder(chosen, hit);  // 配置列表下标更小者优先
        }
      }
    }
    if (chosen != null) {
      index.merge(selector, chosen, RateLimitMvcBinder::earlierInConfigOrder);
    }
  }
}
```

- `syntheticRequest`：至少设置 **method**、**requestURI**（含 contextPath 前缀若测试需要）、**contextPath**，并保证 `RateLimitRules.applicationPath(request)` 与真实派发到该 `HandlerMethod` 的路径一致；优先对 `MockHttpServletRequest` 设置 **`servletPath` / `pathInfo`** 或调用 `ServletRequestPathUtils.parseAndCache(request)`，使 `pathWithinApplication` 与映射 pattern 匹配。
- **禁止** 仅取「第一条映射」代替全量 `(path × method)` 枚举，除非单测证明与上式 `chosen` 相同。
- 绑定完成后：`rules.bindMvcIndex(Map.copyOf(index))`（见 §4.4.4），再执行 §4.4.3 规则覆盖校验。

#### 4.4.3 启动校验（`require-handler-match`）

当 `rose.ratelimit.mvc.require-handler-match=true`（默认）：

- 对每条 **enabled**、**stage=MVC**、且 **path 或 handler 非空** 的规则，存在 **至少一个** HandlerMethod 在 §4.4.2 中被该规则作为 **外部规则** 绑定 → 否则 **启动失败**，消息含 `rule.id`。
- **仅注解**（无 path、无 handler）的规则 **不参与** 此校验。

当 `require-handler-match=false`：

- 不执行上述失败逻辑；运行时对外部 MVC 规则仍 **优先查表**，未命中索引时 **回退** `select(request, MVC, selector)`；启动时对「零绑定」规则打 **WARN**。

#### 4.4.4 运行时与 `RateLimitRules` API

**路径（FILTER + MVC 的 `select` 共用）**：

| 成员 | 约定 |
| --- | --- |
| `RateLimitRules.applicationPath(HttpServletRequest request)` | `static` 或实例方法；返回 `PathContainer.parsePath(ServletRequestPathUtils.parse(request).pathWithinApplication().value())`；**唯一**合法入口，禁止在 `select` 内手写 URI 截取 |

**MVC 索引（启动一次写入）**：

| 成员 | 约定 |
| --- | --- |
| `void bindMvcIndex(Map<String, CompiledRule> externalBySelector)` | 仅在 `RateLimitMvcBinder` / `RateLimitInterceptor.afterSingletonsInstantiated` 调用 **一次**；重复调用 `IllegalStateException` |
| `CompiledRule externalMvcRule(String selector)` | `selector = beanType.getName() + "#" + method.getName()`；无条目返回 `null` |
| `boolean mvcIndexReady()` | 索引是否已绑定（测试用） |

**`RateLimitInterceptor.preHandle`**：

1. `selector` 同上；`CompiledRule external = rules.externalMvcRule(selector)`。
2. 若 `external == null` 且 `require-handler-match == false` → `external = rules.select(request, Stage.MVC, selector)`（**唯一**允许的运行时 `select` MVC 路径）。
3. 若 `external == null` 且 strict → 不执行外部 MVC 规则（仅 `@RateLimit`）。
4. strict 为 true 时 **不得** 在索引已就绪后调用 `select(..., Stage.MVC, ...)`（§9.3 T-MVC-INDEX-01）。

**其它**：

- **配置键始终为 `rule.id`**，不使用 `spring-web@` + 方法签名字符串。
- **scope=HANDLER** 的 bucket 作用域键使用 selector；同一类中同名的重载方法共享额度。

### 4.5 Dispatcher 类型（首版范围）

| Dispatcher | FILTER 限流 | 说明 |
| --- | --- | --- |
| **REQUEST** | 是 | 默认 |
| **ASYNC** | 同 REQUEST 属性去重 | 已测；不重跑 Filter |
| **ERROR** | 否 | `OncePerRequestFilter` 默认跳过 error dispatch |
| **FORWARD** | 否（首版） | 不在 forward 目标上再跑 Filter；README 声明 |
| **INCLUDE** | 否（首版） | 与 FORWARD 同列后续增强 |

---

## 5. 策略、存储与引擎替换

### 5.1 RateLimitPolicy（领域，与引擎无关）

- 字段：`capacity`、`refillTokens`、`refillPeriod`（**greedy token bucket**）。
- 构造时校验：正数、周期 ≥ 1ms；**不得** 引用第三方 `BucketConfiguration`。
- **`fingerprint()`**：`capacity:refillTokens:refillPeriodNanos`，进入 storage 键。
- **`retentionNanos()`**（或等价 `stateTtl()`）：空桶恢复满额时间向上取整 + 固定余量（公式保留在 core，本机 Caffeine 与 Redis TTL 共用逻辑）。

### 5.2 RateLimitStore 契约

```text
RateLimitStore extends RateLimitPlugin
  RateLimitDecision tryAcquire(String storageKey, RateLimitPolicy policy)
```

- **storageKey** = `RateLimitKeys.of(namespace, ruleId, scope, …)` + `:` + `policy.fingerprint()`（实现可在 store 内拼接 fingerprint，与现行为一致）。
- **allowed** / **rejected(retryAfter)**；拒绝时 `retryAfter` 用于 `Retry-After` 头（秒向上取整，最小 1）。
- 仅 **预期后端故障** 抛 **`RateLimitBackendException`**。
- **程序错误** **原样抛出**，不得包装为 backend 以免 OPEN 放行。

### 5.3 默认引擎：Bucket4j

- **本机**：Caffeine + Bucket4j；`max-buckets` 限制 **新身份**；满则 backend 异常，**不** LRU 淘汰活跃桶。
- **Redis**：`rose:ratelimit:v2:` 前缀；Bucket4j Lettuce **CAS**；TTL 按满额恢复时间；独立连接，**不**关闭 Boot 管理的客户端。

**Redis 实现必须**：

- 异常分类见下表；
- `close()`：`closed=true` 且 **`manager=null`**，避免关闭后仍扣减；
- `connect()` 成功、`build()` 失败：**关闭** 已打开连接；
- **已知取舍**：故障重连在 `synchronized` 建连路径上可能串行化命中规则的请求，吞吐降至约 `1/connect-timeout`；P2 可优化，不阻塞 P0。

**Redis `tryAcquire` 异常分类**：

| 处理 | 类型（示例） |
| --- | --- |
| 包装为 `RateLimitBackendException` | Lettuce `RedisException` 及子类、连接/超时、Bucket4j CAS/通信失败、本机 `LocalRateLimitStore` 的容量饱和消息 |
| **禁止** 包装（向上传播） | `IllegalStateException`（已关闭）、`NullPointerException`、`IllegalArgumentException`、非 Store 预期的 `RuntimeException` |

自定义 `RateLimitStore` 遵循同一契约。

### 5.4 替换 Bucket4j

- 提供自定义 `RateLimitStore` Bean（或通过 optional 模块注册 AutoConfiguration）。
- 使用 `@ConditionalOnMissingBean(name = "localRateLimitStore")` 等约定名退让默认实现。
- 新引擎必须通过 **§9 存储契约测试**（并发额度、日额过期、namespace/fingerprint 隔离等）。

### 5.5 Redis 运维语义（不变）

- 需要 `rose.ratelimit.namespace` 或 `spring.application.name`。
- 节点时钟同步；不宣称 Redis TIME。
- 超时可能发生在已扣减之后：**不** 补偿、**不** 重试整请求、**不** 自动降级本机。
- `connect-timeout`（Boot）与 `rose.ratelimit.redis.timeout`（操作）语义分离。
- Cluster：三主 Testcontainers 验证路由与共享额度，**不** 宣称副本故障转移；Sentinel：主从提升后丢弃连接、后续重连，失败请求不重放。

---

## 6. 身份、键与命名空间

- 内置 **ip**（`remoteAddr`）、**principal**、**header**；不解析 X-Forwarded-For；不读 body；不记录凭证。
- 缺失身份默认 **400**；`missing-key=SHARED` 与真实身份隔离。
- 桶逻辑键：`namespace` + `ruleId` + scope + 身份摘要（SHA-256 长度编码），**不等于匿名化**。

---

## 7. 插件与接入扩展

### 7.1 存储与 Resolver（现 SPI，保持）

- `RateLimitPlugin.name()`；`RateLimitStore`、`RateLimitKeyResolver`。
- `AutoConfiguration.imports` 发现；**无** `@EnableResilience4j(plugins=…)`。
- 未知 store/resolver、重复 name → 启动失败。
- 默认 Bean 名：`localRateLimitStore`、`redisRateLimitStore`、`ipRateLimitKeyResolver`、`principalRateLimitKeyResolver`、`headerRateLimitKeyResolver`。

### 7.2 Web 接入（内置，未来可形式化）

- **servlet-filter**：`RateLimitFilter`，`filter-order` 默认 `Integer.MIN_VALUE + 10`，`asyncSupported=true`。
- **spring-mvc**：`RateLimitInterceptor`，order 默认 `Integer.MIN_VALUE + 10`。
- 未来 RPC/MQ 等：**新 optional 模块** 调用同一 `RateLimitExecutor`，不预建重量级插件注册框架；不热加载、无插件市场。

---

## 8. 故障、响应与观测

| 情况 | 行为 |
| --- | --- |
| 额度耗尽 | 可配置 status（默认 429）、body、Retry-After |
| 身份缺失 | 400 problem+json |
| Backend + CLOSED | 503 |
| Backend + OPEN | 放行，result=`backend_error_open` |
| Resolver 异常 | 向上抛出，**不** OPEN |
| 下游业务异常 | 限流 **不** 重试请求 |

- 后端 WARN 日志：每 rule 每分钟最多一次；仅异常类型名，无身份/原始 message。
- Micrometer：`rose.ratelimit.requests{rule,backend,result}`。
- `RateLimitObserver`：失败 **不改变** 已提交决策。
- **可选（后续）**：Spring `ApplicationEvent` 发布 denied/backend 事件，供审计；非首版阻塞项。

---

## 9. 验收与测试

### 9.1 必须覆盖

**原有**：无注解 Filter/MVC、纯 Servlet、规则顺序与方法、注解与去重、异步重派、并发额度、日额过期、本机饱和、未知/冲突插件、功能开关、namespace 隔离、Redis 双实例与故障、OPEN/CLOSED、缺失身份、resolver 程序错误、Retry-After、下游异常不被限流器重试。

**新增（方案强制）**：见 **§9.3** 用例 ID；摘要：

- FILTER：规范化 path 仍受规则约束（容器接受前提下）。
- MVC：strict 启动失败 / 非 strict WARN；strict 运行时走索引。
- Redis：程序错误不 OPEN；建连无泄漏。

### 9.2 构建与发布检查

```sh
mvn spotless:check
mvn verify
```

- Redis Testcontainers 需 Docker；报告 **真实** 构建与测试结果。
- `verify` 会生成 JaCoCo 全 reactor 聚合报告，并要求行覆盖率 ≥80%；覆盖率聚合模块排除 sample，报告在 `rose-ratelimit-coverage/target/site/jacoco-aggregate/`。
- **Release 前**：确认 `rose-ratelimit-bom` 与根 POM 的 `dependencyManagement` 条目一致（§3.2）；BOM 与 starter 构件同版本发布。

### 9.3 新增验收用例（命名建议）

实现时在对应模块添加测试，名称可略有调整，**语义不得弱化**。

| ID | 模块 | 建议测试类 / 场景 |
| --- | --- | --- |
| T-FILTER-PATH-01 | autoconfigure | 映射路径 `/api/auth/./login` 与 `/api/auth/login` 命中同一 FILTER 规则并限流 |
| T-MVC-STRICT-01 | autoconfigure | `require-handler-match=true` 且 MVC path 无映射 → `context` 启动失败 |
| T-MVC-STRICT-02 | autoconfigure | `require-handler-match=false` 同上配置 → 启动成功 + WARN（可断言日志或不断言） |
| T-MVC-INDEX-01 | autoconfigure | strict 模式下 MVC 请求仅走索引（spy 无 `select(..., MVC, ...)`） |
| T-REDIS-OPEN-01 | autoconfigure 或 redis | 已 `close()` 的 store + 规则 OPEN → 非 200（异常穿出或 5xx，不得放行） |
| T-REDIS-CONN-01 | redis | 建连/build 失败不增加泄漏连接（尽力单测） |

---

## 10. 实施状态

早期实施待办已完成。当前交付能力见 §16.10；行为验收场景见 §9.3。新增或修改能力时同步更新对应契约、实现和验收场景。

---

## 11. 与 microsphere-resilience4j / Resilience4j 的关系

Rose 使用 Bucket4j `RateLimitStore` 执行限流；可选 Resilience4j 模块提供熔断和业务调用装饰。与旧 microsphere 集成的模块映射见 §16.9，应用迁移步骤见 [migration-v6.md](migration-v6.md)。

---

## 12. 参考

- [Bucket4j 8.16.x 文档](https://bucket4j.com/8.16.0/toc.html)（默认引擎实现细节）
- [Spring MVC HandlerInterceptor](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-interceptor.html)
- [Spring ServletRequestPathUtils](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/ServletRequestPathUtils.html)（FILTER 路径）
- [microsphere-resilience4j spring-web 用户指南](https://github.com/microsphere-projects/microsphere-resilience4j/blob/main/docs/current/user-guide/zh/microsphere-resilience4j-spring-web.md)（对比与借鉴边界）
- 条件自动配置与 Bean 替换：参考 Spring Boot 惯例及本仓库 `RateLimitAutoConfiguration`

---

## 13. 文档与版本

### 13.1 README 同步

下列变更 **必须** 反映到 README（用户可见）：

- **`rose-ratelimit-bom` import** 与 Starter 坐标（§3.2）。
- FILTER 使用映射路径；forward/include 首版限制（§4.5）。
- MVC 启动绑定与 `rose.ratelimit.mvc.require-handler-match` 默认行为。
- 默认引擎为 Bucket4j 模块（core 可替换）；熔断 optional `starter-resilience4j`；与 microsphere **迁移**（[migration-v6.md](migration-v6.md)）。
- v6 optional starter 表（Feign / MyBatis / Kafka）与 `stage` / `target` 说明。
- `scope=HANDLER` 默认值对多方法控制器的额度放大效应。

### 13.2 行为变更级别（相对 0.1.x 实现）

| 变更 | 级别 | 说明 |
| --- | --- | --- |
| FILTER 映射路径 | **补丁或次版本** | 安全修复；可能使此前「绕过」的请求被限流 |
| MVC `require-handler-match=true` | **次版本** | 错误 path 从「静默不限」变为启动失败 |
| Redis 异常不 OPEN | **补丁** | 程序错误不再误放行 |
| core 脱钩 + bucket4j 模块 | **次版本**（Maven 坐标） | 仅自定义 Store 的 classpath 可能变化；BOM 增加新 artifact |
| 发布 `rose-ratelimit-bom` | **次版本起** | 外部应 import BOM 而非 parent POM |

发布时在 release notes 中列出上表；**首次引入 BOM 的版本**说明推荐 import 方式。

---

## 16. 当前架构（Resilience4j + 多阶段集成）

> **状态**：本节能力均已实现；当前交付范围见 §16.10。
> **决策摘要**：引擎 **B（Resilience4j）** 负责 **CircuitBreaker**；**限流额度** 默认仍 **Bucket4j Store**；配置 **统一 `rules[]`**；**替代 microsphere-resilience4j**（[migration-v6.md](migration-v6.md)）。

### 16.1 目标与非目标

**目标**

- 单一配置面：`rose.ratelimit.rules[]` 描述 **限流 + 熔断**（及后续 resilience 能力）在 **各 stage** 上的行为。
- **Resilience4j** 作为 **熔断 / composite 行为** 实现（`ResiliencePipelineRegistry`、`ResilienceBehaviorRegistry`）；**RateLimiter 不用于默认令牌桶**（额度见 `RateLimitStore`）。
- **启动期绑定**（延续 v5 MVC 思路）：Feign Client#方法、MyBatis statement id、MQ listener、HTTP Handler 等与规则预关联；**strict** 默认启动失败。
- **传输无关核心**：`RoseResilienceInvocation` 负责 identity → 装饰器链 → 结果；HTTP 只负责 **映射到 status/body/Retry-After**。
- **替代 microsphere**：Rose 发布对应的 optional 模块（见 §3.1），迁移指南从 microsphere 模块名映射到 Rose artifact。

**v6.0 非目标（可后续版本）**

- WebFlux 首版、规则热刷新、插件市场。
- **集群一致**的 CircuitBreaker 状态（默认 **JVM 内存**；多实例各自 OPEN/HALF_OPEN，文档必须写明）。
- 用 Resilience4j **完全取代** Bucket4j Redis 分布式限流（v6.0 可 **双引擎并存**，见 §16.8）。

### 16.2 规则模型扩展（仍用 `rules[]`）

在 §4.1 每条规则上增加 **行为** 与 **stage**，兼容 v5 字段。

| 字段 | v5 | v6 说明 |
| --- | --- | --- |
| `id` | 有 | 不变；Resilience4j 实例名后缀：`{namespace}:{id}:rl` / `:cb` |
| `stage` | FILTER, MVC | 增加 **`REST_CLIENT`**、**`FEIGN`**、**`MYBATIS`**、**`JDBC`**、**`MQ`**、**`INVOKE`** |
| `kind` | （无，隐含 rate-limit） | **`rate-limit`**（默认）、**`circuit-breaker`**、**`composite`** |
| `behaviors` | — | `composite` 时有序列表，如 `[rate-limit, circuit-breaker]`；默认先 RL 后 CB |
| `target` | path/handler | 统一 **绑定选择器**（见 §16.3）；HTTP 仍可用 `path` + `handler` |
| `capacity` 等 | 令牌桶 | 仅 `kind` 含 rate-limit 时必填；编译为 `RateLimitPolicy`，由 **Bucket4j Store** 执行 |
| **circuit-breaker** 块 | — | 映射 `CircuitBreakerConfig`（见 §16.4） |
| `store` | RateLimitStore 插件 | **rate-limit** 走 `local` / `redis` 等 Store 插件；**circuit-breaker** 忽略 store（内存 CB Registry） |
| `failure-mode` | OPEN/CLOSED | 仍指 **Registry/后端/程序错误**；**CB OPEN** 走独立 **circuit-open-status**（默认 503） |
| `status` / body | 429 | rate-limit 拒绝：默认 429；CB 拒绝：默认 **503**（可 per-rule 覆盖） |

**编译期校验**

- stage 与字段组合合法（如 FILTER 禁止 `target` 为 Feign）。
- `composite` 至少两种 behavior 之一生效。
- 引用的 Resilience4j 参数范围合法（与 Resilience4j 校验一致，失败 **启动失败**）。

### 16.3 stage 与绑定（启动期）

| stage | 绑定来源 | `target` / 匹配示例 | 运行时入口 |
| --- | --- | --- | --- |
| FILTER / MVC | v5 同 | `path`, `methods`, `handler` | Filter / Interceptor |
| REST_CLIENT | 请求时按 URI 选择 | `rest-client:https://payments.example.com`，可组合 `path` / `methods` | Spring Boot `RestClientCustomizer` 拦截器 |
| FEIGN | Feign Client interface 与方法 | `feign:PaymentClient#createOrder` | Feign `InvocationHandler` 装饰 |
| MYBATIS | `Configuration` 内 `MappedStatement` id | `mybatis:com.foo.UserMapper.selectById` | `Interceptor` |
| JDBC | Alibaba Druid datasource | `jdbc:sql-table:orders` | Druid Filter |
| MQ | Kafka / Rabbit listener | `mq:kafka:topic-orders#groupId` 或 `mq:rabbit:orders#listener-id` | Kafka `RecordInterceptor` / Rabbit receive guard |
| INVOKE | 显式编程式调用 | `invoke:com.foo.Service#charge` | `RoseResilience.run/call(...)` |

各 stage 使用独立的 strict 属性进行绑定校验，例如 `rose.ratelimit.mvc.require-handler-match`、`rose.ratelimit.feign.require-client-match`、`rose.ratelimit.mybatis.require-statement-match`、`rose.ratelimit.jdbc.require-datasource-match` 和 `rose.ratelimit.mq.require-listener-match`；不存在统一的 `rose.ratelimit.binding.strict`。

### 16.4 CircuitBreaker 配置映射（YAML 草案）

```yaml
rose:
  ratelimit:
    rules:
      - id: payment-feign
        stage: FEIGN
        kind: composite
        behaviors: [rate-limit, circuit-breaker]
        target: feign:PaymentClient#createOrder
        capacity: 50
        refill-tokens: 50
        refill-period: 1s
        circuit-breaker:
          failure-rate-threshold: 50
          slow-call-rate-threshold: 100
          slow-call-duration: 2s
          minimum-number-of-calls: 10
          sliding-window-size: 20
          wait-duration-in-open-state: 30s
          permitted-calls-in-half-open-state: 5
        circuit-open-status: 503
        failure-mode: CLOSED
```

实现时将上述键 **1:1 映射** 到 `io.github.resilience4j.circuitbreaker.CircuitBreakerConfig`（命名与 Resilience4j 属性保持一致，减少认知负担）。

### 16.5 已支持的 Resilience4j 能力

| 能力 | 支持 | 说明 |
| --- | --- | --- |
| 令牌桶限流 | **是（Bucket4j Store）** | `RateLimitStore`（local/redis），**非** Resilience4j RateLimiter |
| CircuitBreaker | **是（必选）** | `ResiliencePipelineRegistry` |
| Retry | **是（composite + `behaviors` 含 retry）** | |
| TimeLimiter | **是（composite + `behaviors` 含 time-limiter）** | |
| Bulkhead | **是（composite + `behaviors` 含 bulkhead）** | |

**装饰顺序（默认）**：入口 **限流 / 熔断**（Bucket4j + Resilience4j CB）→ **Bulkhead → Retry → TimeLimiter → 业务**（`invokeGuarded` 各 stage；HTTP **`stage: FILTER`** 在放行后用 `ResilienceHttpFilterChain` 包装 `FilterChain`；**不**对 429/503 做 Retry/TL/BH；**MVC 规则**需在相同 path 上使用 FILTER 或 INVOKE 才能装饰 HTTP 调用）→ 业务；CB 记录慢调用/失败，RL 限制入口 QPS。

composite + retry 示例：

```yaml
kind: composite
behaviors: [rate-limit, retry]
retry:
  max-attempts: 3
  wait-duration: 500ms
```

需 classpath 上有 `rose-ratelimit-spring-boot-starter-resilience4j`（`resilience4j-retry`）。

```yaml
kind: composite
behaviors: [rate-limit, time-limiter]
time-limiter:
  timeout-duration: 3s
  cancel-running-future: true
```

可与 `retry` 同规则并用；装饰顺序为 **Bulkhead → Retry → TimeLimiter → 业务**（每次重试单独受超时约束）。

```yaml
kind: composite
behaviors: [rate-limit, bulkhead]
bulkhead:
  max-concurrent-calls: 10
  max-wait-duration: 0ms
```

### 16.6 核心抽象（重构 v5）

```text
rules[] → RuleCompiler → CompiledPolicy（含 RL/CB 配置、stage、target）
       → BindingRegistry（各 stage 索引）
       → RoseResilienceInvocation.invoke(context, policyId)
            → IdentityResolver（按 stage 从 context 取 key）
            → DecoratorChain（Resilience4j）
            → Outcome（allowed / rate-limited / circuit-open / backend-error）
       → StageAdapter（HTTP 写响应 / Feign 抛异常 / MyBatis 抛 SQLException 等）
```

- **已落地**：HTTP 决策走 **`RateLimitGuardedInvocation.evaluate` + `HttpRateLimitStageSemantics`**；`RateLimitExecutor` 仅负责请求去重与写 Servlet 响应。
- **`RateLimitStore` SPI**：v6.0 保留；**rate-limit** 额度一律经 Store（默认 Bucket4j local，**redis** 走 starter-redis）。

### 16.7 HTTP 语义（含熔断）

| 结果 | 默认 HTTP | 说明 |
| --- | --- | --- |
| 允许 | 继续 filter chain | |
| 限流 | **429** + Retry-After | 与 v5 一致 |
| 熔断 OPEN | **503** + problem+json | **不是** `failure-mode` OPEN；CB 是业务保护 |
| 身份缺失 | 400 | 不变 |
| Registry/后端异常 | OPEN→放行 / CLOSED→503 | 与 v5 failure-mode 一致 |

### 16.8 分布式与双引擎

|  concern | v6.0 策略 |
| --- | --- |
| 限流多实例共享额度 | **优先** 保留 **Bucket4j Redis** 插件（`store: redis`）；Resilience4j RL 默认 **每 JVM** |
| 熔断多实例 | **每 JVM 独立** CB；文档说明与 microsphere/Resilience4j 默认一致 |
| 长期 | 评估 Resilience4j 生态/redis 或统一回 Rose Store 适配 RL only |

**对抗式结论**：若业务强依赖 **集群精确 QPS**，v6.0 仍应显式配置 **redis + bucket4j store**，不能假设 Resilience4j RL 替代 Redis 桶。

### 16.9 替代 microsphere 迁移

| microsphere 模块 | Rose v6 模块 | 配置迁移 |
| --- | --- | --- |
| `microsphere-resilience4j-spring-web` | autoconfigure（已有 HTTP） | `@RateLimit` / rules stage MVC |
| `microsphere-resilience4j-openfeign` | `rose-ratelimit-spring-cloud-starter-openfeign` | Feign `target` + `rules[]` |
| `microsphere-resilience4j-mybatis` | `rose-ratelimit-spring-boot-starter-mybatis` | `mybatis:` target |
| `microsphere-resilience4j-alibaba-druid` | `rose-ratelimit-spring-boot-starter-alibaba-druid` | `jdbc:` / druid 规则 |

移除 microsphere 依赖后，**不再** 使用 `@EnableResilience4j`；Rose 仍 **无** Enable，仅 `AutoConfiguration.imports` + optional 模块 classpath。

### 16.10 当前交付范围

| 能力 | 当前实现 |
| --- | --- |
| HTTP | Servlet Filter、Spring MVC、外部规则和可选 `@RateLimit` |
| 存储 | 本机 Bucket4j 与 Redis Bucket4j store |
| 集成阶段 | RestClient、OpenFeign、MyBatis、Druid JDBC、Kafka、RabbitMQ、编程式 INVOKE |
| Resilience4j | 熔断、重试、TimeLimiter、Bulkhead；HTTP 业务装饰使用 FILTER stage |
| 扩展与观测 | `RateLimitStore`、`RateLimitKeyResolver`、`RateLimitObserver` |

---

*文档版本：6；版本变更记录见 [CHANGELOG.md](../CHANGELOG.md)。*
