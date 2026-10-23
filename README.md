# Rose Rate Limit Spring Boot Starter

[![CI](https://github.com/zhijun-io/rose-ratelimit-spring-boot-starter/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/zhijun-io/rose-ratelimit-spring-boot-starter/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
[![GitHub stars](https://img.shields.io/github/stars/zhijun-io/rose-ratelimit-spring-boot-starter)](https://github.com/zhijun-io/rose-ratelimit-spring-boot-starter/stargazers)
[![License](https://img.shields.io/github/license/zhijun-io/rose-ratelimit-spring-boot-starter)](LICENSE)

无需业务注解或代码调用，通过外部配置对 Servlet / Spring MVC HTTP 请求限流。支持 Boot 4.1.1、Java 25，使用 Bucket4j 8.16.1 Token Bucket。

## 引入

```xml
<dependency>
    <groupId>io.zhijun</groupId>
    <artifactId>rose-ratelimit-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

默认本机状态。应用自行选择 Web 服务器及 MVC 依赖；已有 MVC 应用继续使用 spring-boot-starter-webmvc，本 Starter 不强制安装 MVC。

Redis 将依赖替换为 rose-ratelimit-spring-boot-starter-redis，再设置 store.type=redis。仅引入 Redis 依赖不会自动切换默认后端。

## 示例应用

可运行示例位于 [rose-ratelimit-spring-boot-sample](rose-ratelimit-spring-boot-sample/README.md)，包含无注解 Filter/MVC 限流和可选 `@RateLimit` 用法。

## 无注解配置

```yaml
spring:
  application:
    name: example-api
rose:
  ratelimit:
    rules:
      - id: login-ip
        stage: FILTER
        path: /api/auth/login
        methods: [POST]
        capacity: 5
        refill-tokens: 5
        refill-period: 1m
        key-source: ip
        failure-mode: CLOSED
      - id: item-read
        stage: MVC
        path: /api/items/**
        methods: [GET]
        capacity: 20
        refill-tokens: 20
        refill-period: 1s
        key-source: principal
      - id: item-write
        stage: MVC
        path: /api/items/**
        methods: [POST, PUT, DELETE]
        capacity: 5
        refill-tokens: 5
        refill-period: 1s
        key-source: principal
```

Filter 覆盖普通 Servlet 和由其他过滤器处理的登录端点。MVC 使用已解析的 Controller 方法，无需业务注解，默认按方法分别计算额度。

每个阶段按列表顺序取第一个匹配的启用规则。未匹配放行。桶初始满额，按 refill-period 持续补充 refill-tokens，允许 capacity 范围内突发。

## 规则

| 配置 | 默认值或含义 |
| --- | --- |
| id | 必填、唯一，字母数字及 ._- |
| enabled | true |
| stage | FILTER，可选 MVC |
| path | context path 之后的路径，Spring PathPattern，如 /api/** |
| methods | 空列表匹配全部 HTTP 方法 |
| handler | MVC 可选类全名#方法名，支持 *，如 com.example.*#export |
| scope | FILTER 默认为 RULE，MVC 为 HANDLER；RULE 共用同规则额度 |
| capacity / refill-tokens / refill-period | 10 / 10 / 1s；最小周期 1ms |
| key-source | ip，或 principal、header、自定义 resolver 名 |
| key-header | header 来源必填 |
| missing-key | REJECT 返回 400；SHARED 共用缺失身份桶 |
| store | 继承全局 store.type，默认 local |
| failure-mode | 继承全局 OPEN；CLOSED 在后端异常时返回 503 |
| status / content-type / body | 429 / application/problem+json / 自动生成的错误体 |

path 不是正则，非法模式启动失败。配置了启用的 MVC 规则但缺少 MVC classpath 时也会启动失败（显式 mvc-enabled=false 除外）。MVC 无 path/handler 的规则仅供注解使用。不同阶段、不同规则可叠加；不是跨桶事务，后续拒绝不退还前面的额度。

## 可选注解

```yaml
rose:
  ratelimit:
    rules:
      - id: export
        stage: MVC
        capacity: 2
        refill-tokens: 2
        refill-period: 1m
        key-source: principal
```

```java
import io.zhijun.ratelimit.web.RateLimit;

@RateLimit("export")
@GetMapping("/api/export")
public String export() {
    return "result";
}
```

支持 Controller 类或方法，方法注解覆盖类。注解只引用外部规则 ID，不是 Service AOP 注解。显式绑定后不使用该规则的 path/methods/handler 选择条件，其他配置仍有效。

外部 MVC 匹配先执行，注解随后执行。同 ID 同一请求仅扣一次，包括异步重新分派；不同 ID 各扣一次。注解不能撤销入口 Filter 的规则。引用不存在或非 MVC 规则在启动时失败。

## Redis

```yaml
spring:
  application:
    name: example-api
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      connect-timeout: 1s
      timeout: 1s
rose:
  ratelimit:
    namespace: production-example-api
    store:
      type: redis
    redis:
      timeout: 1s
      max-retries: 8
```

namespace 默认取 spring.application.name；Redis 两者都未设置时启动失败。同应用所有副本使用相同 namespace，不同应用/环境隔离。

使用 Boot Lettuce 配置，独立限流连接按需创建并自动关闭。Bucket4j CAS 原子更新，TTL 按恢复满额时间计算。节点需要时钟同步。操作 timeout 不包括初次连接的全部时间，连接使用 Boot connect-timeout。

已通过 standalone Redis、三主节点 Redis Cluster 和 Redis Sentinel 主从提升的 Testcontainers 验证。Cluster 测试覆盖路由及共享额度，不覆盖副本故障转移；Sentinel 测试验证提升后后续请求重建连接并保留复制的桶状态。连接断开时当前请求返回后端错误，不会自动重放；后续请求重新建连。参数指纹进入键，修改额度会重置桶，滚动发布中不同版本短暂使用各自的桶。新实现使用 v2 前缀，旧版 Lua 状态等待原 TTL 到期，不自动迁移。

## 身份、顺序和故障

Filter 默认 order=Integer.MIN_VALUE+10，适合认证前 IP 限流。principal 要用 filter-order 放到建立身份的认证过滤器之后，或选择 MVC。认证 Filter 若已结束登录请求，排在它之后的 Filter 无法保护该请求。

ip 使用 remoteAddr，不自行解析转发头；部署方负责可信代理。header 必须来自可信身份来源，任意可变请求头不能证明身份。不读取请求体，不使用密码或 Token。

身份缺失默认 400；额度耗尽默认 429 和向上取整的 Retry-After。只有后端异常使用 OPEN/CLOSED；程序错误不自动放行。默认 OPEN 优先可用性，登录等保护规则建议 CLOSED。异常日志按规则每分钟限频。

本机默认最多 100000 个桶，由 local.max-buckets 调整。TTL 至少覆盖恢复满额时间；容量不足时报后端错误，不淘汰活跃桶。OPEN 会放行这种情况下的新身份。计数跨实例不共享，重启丢失。本机短同步区串行化入场和扣减，吞吐需按应用负载评估。

## 插件与观测

实现 RateLimitStore 或 web.RateLimitKeyResolver 并注册为 Spring Bean，通过唯一 name 在规则中引用。缺少插件或名称冲突时启动失败。

```java
@Bean
RateLimitKeyResolver partnerKeyResolver() {
    return new RateLimitKeyResolver() {
        public String name() { return "partner"; }
        public String resolve(HttpServletRequest request, String headerName) {
            return (String) request.getAttribute("trustedPartnerId");
        }
    };
}
```

设置 key-source=partner。null/空白表示身份缺失；validate(headerName) 提供启动校验。后端不可用应抛 RateLimitBackendException。

默认 Bean 名：localRateLimitStore、redisRateLimitStore、ipRateLimitKeyResolver、principalRateLimitKeyResolver、headerRateLimitKeyResolver。同名 Bean 可使默认配置退让；新增能力使用新名称。插件包通过 AutoConfiguration.imports 自动注册，不需要 Enable 注解，没有热加载或独立插件生命周期。

存在 Micrometer MeterRegistry 时记录 rose.ratelimit.requests，标签为 rule、backend、result。也可实现 RateLimitObserver，观察失败不影响额度决策。

## 开关与构建

- enabled=false 关闭整个限流功能；filter-enabled=false / mvc-enabled=false 独立关闭阶段。
- 当前仅 Token Bucket、Servlet/MVC、请求级去重；不含 WebFlux、业务配额事务、请求体账号提取和在线刷新。
- 早期版本迁移：key resolver 移到 web 包并改为具名接口；store 需有 name；路径改用 PathPattern；缺失身份由隐式共享变为默认拒绝。
- 不再输出早期 RateLimit-Limit/Remaining 字段，使用 Retry-After。

```sh
mvn spotless:check
mvn verify
```

Redis Testcontainers 测试需要 Docker。完整方案见 [docs/development-prompt.md](docs/development-prompt.md)。
