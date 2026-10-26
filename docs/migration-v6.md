# v6 迁移指南（microsphere → Rose）

面向从 [microsphere-resilience4j](https://github.com/microsphere-projects/microsphere-resilience4j) 或纯 v5 HTTP 配置迁移到 **Rose v6**（统一 `rose.ratelimit.rules[]` + optional 模块）的团队。

行为与验收细节仍以 [development-prompt.md](development-prompt.md) §16 为准。

## 1. 依赖对照

| 能力 | microsphere（示意） | Rose v6 |
| --- | --- | --- |
| HTTP 限流 / 熔断 | `microsphere-resilience4j-spring-web` | `rose-ratelimit-spring-boot-starter` + 可选 `starter-resilience4j` |
| OpenFeign | `microsphere-resilience4j-openfeign` | `rose-ratelimit-spring-cloud-starter-openfeign` |
| MyBatis | `microsphere-resilience4j-mybatis` | `rose-ratelimit-spring-boot-starter-mybatis` |
| Druid JDBC | `microsphere-resilience4j-alibaba-druid` | `rose-ratelimit-spring-boot-starter-alibaba-druid` |
| Kafka / Rabbit 消费 | （各项目自建） | `rose-ratelimit-spring-boot-starter-mq` + `spring-kafka` / `spring-boot-starter-amqp` |
| 分布式令牌桶 | 依 microsphere 存储 | `rose-ratelimit-spring-boot-starter-redis` + `store: redis` |

版本对齐：在 `dependencyManagement` 中 import `rose-ratelimit-bom`，再按需声明 starter（勿混用未纳入 BOM 的版本）。

**无需** `@EnableResilience4j` 或 Rose 侧 `@Enable*`：各模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动注册。

## 2. 配置模型

- 所有场景共用 **`rose.ratelimit.rules[]`**，用 **`stage`** 区分入口：`FILTER` / `MVC` / `FEIGN` / `MYBATIS` / `JDBC` / `MQ` / `INVOKE`。
- 非 HTTP 场景用 **`target`**（或 `handler`）作为绑定选择器，前缀约定见 §16.3。
- 熔断与组合策略：`kind: rate-limit`（默认）、`circuit-breaker`、`composite`；熔断 OPEN 走 **`circuit-open-status`**（默认 503），与 `failure-mode`（存储后端异常）分离。
- **Retry**（v6.x 已落地）：`kind: composite` 且 `behaviors` 含 **`retry`**，配置 **`retry.max-attempts`** / **`retry.wait-duration`**；限流或熔断拒绝**不会**进入重试。需 **`starter-resilience4j`**。适用于 INVOKE、Feign、MyBatis、JDBC、MQ 等业务 Callable；HTTP 请用 **`stage: FILTER`**（同 path）包装整条 Filter 链。
- **TimeLimiter**：`behaviors` 含 **`time-limiter`**，配置 **`time-limiter.timeout-duration`**（及可选 **`cancel-running-future`**）。在限流/熔断放行后限制业务 Callable 时长；可与 retry 组合（每次重试单独计时）。
- **Bulkhead**：`behaviors` 含 **`bulkhead`**，配置 **`bulkhead.max-concurrent-calls`** / **`max-wait-duration`**。限制并发业务 Callable；满员且不可等待时抛 `BulkheadFullException`。

### 2.1 target 示例

| stage | target 示例 | 运行时 selector |
| --- | --- | --- |
| FEIGN | `feign:PaymentClient#createOrder` | `com.example.PaymentClient#createOrder` |
| MYBATIS | `mybatis:com.foo.UserMapper.selectById` | MappedStatement id |
| JDBC | `jdbc:sql-table:orders` | `sql-table:orders`（从 SQL 解析表名，小写） |
| MQ | `mq:kafka:orders#payment-group` | `kafka:orders#payment-group` |
| MQ | `mq:rabbit:orders#listener-id` | `rabbit:orders#listener-id` |
| INVOKE | `invoke:com.foo.Service#charge` | 编程式 `RoseResilience.call(ruleId, …)` |

各 stage 的 **strict 绑定**（未匹配则启动失败）：

| 属性 | 默认 |
| --- | --- |
| `rose.ratelimit.mvc.require-handler-match` | true |
| `rose.ratelimit.feign.require-client-match` | true |
| `rose.ratelimit.mybatis.require-statement-match` | true |
| `rose.ratelimit.jdbc.require-datasource-match` | true |
| `rose.ratelimit.mq.require-listener-match` | true |

## 3. 限流引擎与 Bucket4j（重要）

|  Concern | v6.0 策略 |
| --- | --- |
| **入口限流（QPS）** | 仍由 **`RateLimitStore` + Bucket4j** 执行（`store: local` 或 `store: redis`） |
| **熔断** | **Resilience4j CircuitBreaker**（`starter-resilience4j`），每 JVM 独立 |
| **Resilience4j RateLimiter** | v6.0 **未**替代 Bucket4j 作为默认存储；勿假设其可共享 Redis 集群额度 |

说明（文档级「勿再默认只谈 Bucket4j 做熔断」）：v6 的 **熔断** 已交给 Resilience4j；**限流额度** 在需要多实例一致时仍应显式 **`starter-redis` + Bucket4j**。`rose-ratelimit-bucket4j` **未废弃、未移除**，仍是默认本机与 Redis 实现路径。

## 4. HTTP 行为变更摘要

- 限流拒绝：**429** + Retry-After（与 v5 一致）。
- 熔断 OPEN：**503** + problem+json 风格 body（`circuit-open-status` 可配）。
- 身份缺失：**400**（`missing-key: REJECT`）。

## 5. 迁移步骤建议

1. 引入 BOM + 原 `starter`；跑通现有 FILTER/MVC 规则（回归 HTTP 集成测同等场景）。
2. 需要熔断时增加 `starter-resilience4j`，将 fragile 端点改为 `kind: composite` 或 `circuit-breaker`。
3. 移除 microsphere 依赖与 `@EnableResilience4j`；按上表替换 optional 模块。
4. 将 Feign / MyBatis / Kafka 注解或 XML 配置对照 **target** 做启动期绑定校验（strict=true 下未绑定即失败）。
5. Redis 生产环境确认 `rose.ratelimit.namespace` 与 `spring.application.name` 隔离策略。

## 6. 编程式 INVOKE

```yaml
rose:
  ratelimit:
    rules:
      - id: charge
        stage: INVOKE
        target: invoke:com.example.BillingService#charge
        capacity: 100
        refill-period: 1s
```

```java
@Autowired RoseResilience resilience;

resilience.run("charge", () -> billingService.charge(order));
```

拒绝时抛出 `RateLimitInvokeRejectedException`（含 `status()` / `result()`）。`rose.ratelimit.invoke.enabled` 默认 `true`。

**非 Web 应用**（`spring.main.web-application-type=none`）：仅需 `RateLimitSharedAutoConfiguration` 路径（starter 自动注册），无需 Servlet；HTTP Filter/MVC 不会加载。若 classpath 有 Micrometer `MeterRegistry`，`rose.ratelimit.requests` 指标同样生效。
