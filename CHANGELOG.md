# Changelog

All notable changes to this project are documented here. Versioning follows the repository BOM (`0.1.0-SNAPSHOT` until the first release tag).

## Unreleased (v6)

### Added

- **Resilience4j integration** (`rose-ratelimit-spring-boot-starter-resilience4j`): circuit breaker on HTTP and non-HTTP stages; `kind: rate-limit` | `circuit-breaker` | `composite`.
- **Stages**: OpenFeign, MyBatis, Druid JDBC, Kafka/Rabbit MQ, programmatic **INVOKE** (`RoseResilience`).
- **Composite behaviors** (after rate-limit / circuit allow): **Retry**, **TimeLimiter**, **Bulkhead** via unified `ResilienceBehaviorRegistry` / `ResilienceBusinessDecorators` / `ResilienceHttpFilterChain` (`stage: FILTER` for Servlet-chain decoration).
- **Non-web** support: `RateLimitSharedAutoConfiguration`, metrics without Servlet.
- **Docs**: [docs/migration-v6.md](docs/migration-v6.md), [docs/development-prompt.md](docs/development-prompt.md) §16.
- **Sample**: resilience starter, `fragile-cb` (MVC circuit breaker), `demo-retry` (FILTER retry), `demo-bulkhead` (FILTER bulkhead).

### Changed

- Retry / time-limiter / bulkhead engines expose a single **`ResilienceBehaviorRegistry`** bean (`Resilience4jBehaviorRegistry`); removed separate `ResilienceRetryRegistry`, `ResilienceTimeLimiterRegistry`, and `ResilienceBulkheadRegistry` types.
- **Maven reactor slimmed**: removed empty `starter-*` aggregator modules; integration code ships as the published starter coordinates (`starter-openfeign`, `starter-resilience4j`, …). Kafka + Rabbit merged into **`rose-ratelimit-spring-boot-starter-mq`** (add `spring-kafka` / `spring-boot-amqp` as needed). Removed standalone artifacts `rose-ratelimit-feign`, `-kafka`, `-rabbit`, `-redis`, `-resilience4j`, etc.
- **MQ cohesion**: `RateLimitMqExecutor`, stage semantics, and `RateLimitMqAutoConfiguration` moved from autoconfigure into **`starter-mq`** (Kafka/Rabbit auto-config loads after MQ beans).
- **HTTP guarded path**: Filter/MVC decisions use `RateLimitGuardedInvocation.evaluate`; `RateLimitExecutor` is servlet response + per-rule dedup only.

### Behavior

- Circuit breaker **OPEN** → configurable **`circuit-open-status`** (default **503**); rate limit reject remains **429**.
- Retry / time-limiter / bulkhead **do not** run on 429/503 rejections.
- Startup **WARN** when `stage: MVC` rules declare retry/time-limiter/bulkhead (use FILTER or INVOKE instead).

### Dependencies

- Spring Boot **4.1.2**, Java **25**, Bucket4j **8.16.1**, Resilience4j **2.2.0**.

## v5 (HTTP baseline)

- Configuration-driven Servlet / MVC rate limiting with Bucket4j store plugins (local, Redis).
- Optional `@RateLimit` annotation referencing external rule ids.
