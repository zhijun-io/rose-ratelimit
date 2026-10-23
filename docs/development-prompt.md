# Rose Rate Limit Starter：完整方案与开发提示词

本文是经审核后确认的首版实现依据，替代早期自行编写 Java/Lua Token Bucket 的方案。继续开发前阅读本文和 README。

## 目标

引入依赖并配置规则即可限流，无需业务注解、业务 API 调用或 Enable 注解。覆盖 Servlet Filter、登录等由过滤器处理的请求，以及 Spring MVC HandlerMethod。注解是可选的显式规则引用。

保持 Spring Boot 4.1.1、Java 25、Maven。首版使用 Bucket4j 8.16.1 greedy Token Bucket，提供本机与 Redis 后端。暂不增加其他算法、WebFlux、Service AOP、请求体账号解析、业务配额事务或在线规则刷新。

## 模块与执行路径

| 模块 | 职责 |
| --- | --- |
| core | 策略、决策、插件与存储 SPI、后端异常、观察接口、Bucket4j 本机适配 |
| autoconfigure | 配置绑定、启动编译、Servlet/MVC 接入、key resolver、共同执行核心、可选指标 |
| redis | Bucket4j Lettuce CAS 适配和条件自动配置 |
| starter | Servlet 默认依赖，不强制引入 MVC 或嵌入式服务器 |
| starter-redis | 聚合默认 Starter 与 Redis 适配 |

core 不依赖 Servlet/Spring MVC。请求身份解析接口属于 web 包。启动编译器校验配置及插件引用，生成不可变规则；运行时不持有可变 ConfigurationProperties。

执行路径：外部配置 → 启动规则编译 → Filter/MVC → RateLimitExecutor → RateLimitStore。

共同执行核心负责身份与作用域、请求去重、后端故障策略、响应和观测；Filter/MVC 只负责选择规则和接入生命周期。

## 规则及双接入语义

1. 每条规则有唯一 ID，stage 默认 FILTER，可选 MVC。
2. FILTER 必须有 path，使用应用 context path 之后的路径；MVC 支持 path、methods 和 handler。handler 为类全名#方法名，支持 *。
3. path 使用 Spring PathPattern，启动解析；不是正则或旧 AntPathMatcher 语法。
4. 每个阶段按配置顺序执行第一个匹配的启用规则；无匹配放行。MVC 不会在没有规则时给所有 Controller 隐式增加限额。已启用的 MVC 规则在缺少 MVC classpath 时启动失败；显式 mvc-enabled=false 除外。
5. FILTER 默认 scope=RULE，同规则多个端点共用额度。MVC 默认 scope=HANDLER，不同 Controller 方法、重载方法分别计费。
6. MVC 无 path/handler 的规则仅供注解引用。
7. @RateLimit("id") 支持 Controller 类或方法，方法覆盖类；启动检查实际注册映射上的注解必须引用存在的 MVC 规则。
8. 外部 MVC 匹配规则先执行，注解引用随后执行。不同 ID 是显式叠加，相同 ID 同一请求只扣一次。注解不能撤销入口 Filter 规则。
9. 注解显式绑定方法，不再应用规则的 path/methods/handler 选择条件；enabled、额度、key、故障和响应配置始终有效。
10. 请求属性记录执行结果，异步重新分派复用；错误分派不重跑入口 Filter。同请求转发到另一方法时，已执行的同 ID 规则不再计费。
11. 多条规则不是跨桶事务；后续拒绝不会退还之前消耗的额度。
12. filter-enabled / mvc-enabled 分别关闭接入层；enabled=false 关闭整个限流功能。

## 算法及状态生命周期

- 本机与 Redis 统一使用 Bucket4j，不维护自写额度算法。周期至少 1ms，统一校验参数及可表示的恢复时间。
- 本机 Caffeine 闲置 TTL 至少覆盖空桶恢复满额的时间并加余量；不固定为一小时。
- 最大桶数限制的是新身份入场。容量满时报后端异常，不能淘汰活跃桶并重置额度。规则 OPEN/CLOSED 决定故障时放行或 503。
- 本机入场和扣减在一个短同步区完成，不包含 IO；这是首版吞吐取舍。跨实例不共享，重启丢失状态。
- Redis 使用 Bucket4j Lettuce CAS 和库内原子脚本、按恢复满额时间计算 TTL。有界重试和操作超时。
- Redis 复用 Boot LettuceConnectionFactory 的原生客户端，独立连接按需创建并在关闭时释放，不关闭 Boot 客户端。
- standalone Redis、三主节点 Redis Cluster 与 Sentinel 主从提升已有 Testcontainers 验证。Cluster 仅验证客户端路由与跨 store 共享额度，不覆盖副本故障转移。Sentinel 提升后，当前失败请求不重放并返回后端错误；存储丢弃失效连接，后续请求重新连接并使用提升后的主节点。
- 分布式状态计算使用应用节点时钟，需要时钟同步，不宣称使用 Redis TIME。
- 策略指纹进入键：改动容量/补充参数会创建新桶。滚动发布时不同配置版本会短暂使用不同额度，首版不是无损热更新。
- Redis 超时可能发生在已经扣减但客户端没收到结果之后；不自动补偿，不重试整个 HTTP 请求，不自动切换本机额度。
- 初次连接受 Boot connect-timeout 约束；limiter timeout 约束操作，不能描述为包含初次连接的总截止时间。

## 身份与命名空间

- 内置 ip、principal、header；默认 ip 使用 remoteAddr，不自行解析转发头。可信代理导致的 remoteAddr 重写由部署方负责。
- principal 需要认证已完成；Filter 顺序须配置到建立身份的过滤器之后，或使用 MVC 阶段。
- header 仅适用于可信标识来源；可由客户端任意更换的 header 不能证明用户身份。
- 身份缺失默认返回 400；missing-key=SHARED 显式共用缺失桶，与真实身份值隔离。
- 不读取请求体或记录密码、Token、完整身份。
- 桶键包含应用 namespace、规则 ID、方法作用域和身份的长度编码摘要，以及策略指纹。SHA-256 限制键长度，不等于匿名化。
- Redis 要求 namespace 或 spring.application.name。相同应用的副本使用同 namespace，不同应用/环境隔离。
- Redis 使用 v2 前缀，与早期 Lua 数据不兼容；不扫描、迁移、删除旧键，等待原 TTL 到期。

## 轻量插件

- RateLimitPlugin 只提供 name。RateLimitStore、Web RateLimitKeyResolver 提供实际能力。
- 通过 Spring Bean、Boot AutoConfiguration.imports 发现；不要求业务 Enable 注解。
- 名称全局唯一，未知后端/resolver、重复名称、依赖缺失启动失败。插件顺序不影响按配置顺序匹配规则。
- 自定义 resolver 以新名称共存。替换默认能力使用 README 中约定的 Bean 名称使默认配置退让。
- 后端内部封装算法差异；首版策略只表达 Token Bucket，不暴露任意算法参数表达式。
- 其他请求适配器可通过独立自动配置接共同核心，不预建通用适配器插件注册框架。
- 不实现热加载、插件依赖图、独立生命周期或插件市场。

## 故障、响应和观测

- 全局默认 OPEN 优先可用性；每条规则可覆盖，登录等保护规则推荐 CLOSED。
- 仅 RateLimitBackendException 参与策略。resolver 和程序错误不能被当成后端故障放行。
- 额度拒绝默认 429，Retry-After 向上取整；可配置拒绝状态、Content-Type、响应体。
- 身份缺失 400，后端 CLOSED 503，与额度拒绝分开。默认 problem+json。
- 不再输出早期 RateLimit-Limit/Remaining 字段，不将其声明为已标准化协议。
- 后端异常日志按规则每分钟最多一次，不输出身份或原始异常消息。
- 可选 Micrometer 标签仅为规则 ID、后端、固定结果；不使用原始 URL/IP/用户/请求头值。
- 观察器失败不改变已完成的限流决策。

## 验收

必须覆盖：无注解 Filter/MVC、纯 Servlet、规则顺序与 HTTP 方法、注解引用及去重、异步重派、并发额度、日额度过期、缓存饱和、未知/冲突插件、功能关闭、命名空间隔离、真实 Redis 双实例共享与故障、按规则 OPEN/CLOSED、缺失身份、resolver 程序错误、Retry-After、下游异常不被重试。

执行格式检查、编译、测试和打包，报告真实结果。Redis Testcontainers 要求 Docker。Cluster 测试使用三主节点，不能把它扩大为副本故障转移验证；Sentinel 故障切换需由 Sentinel 主从提升集成测试单独验证。

## 参考

- [Bucket4j 官方文档](https://bucket4j.com/8.16.0/toc.html)：算法、Lettuce、CAS 与过期策略。
- [Bucket4j 8.16.1 POM](https://repo.maven.apache.org/maven2/com/bucket4j/bucket4j_jdk17-lettuce/8.16.1/bucket4j_jdk17-lettuce-8.16.1.pom)：坐标和 Apache 2.0 声明。仅通过依赖使用，没有复制第三方实现。
- [Spring MVC 拦截器](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-interceptor.html)：preHandle 执行时机。
- 本地 Rose Audit 仅参考条件自动配置和 Bean 替换模式，不引入审计业务模型。
