# Rose Rate Limit

[![CI](https://github.com/zhijun-io/rose-ratelimit/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/zhijun-io/rose-ratelimit/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Spring Boot 4.1.2](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)
[![Line coverage](https://img.shields.io/badge/line_coverage-%E2%89%A5_80%25-brightgreen)](#coverage)
[![GitHub stars](https://img.shields.io/github/stars/zhijun-io/rose-ratelimit)](https://github.com/zhijun-io/rose-ratelimit/stargazers)
[![License](https://img.shields.io/github/license/zhijun-io/rose-ratelimit)](LICENSE)

Rose Rate Limit applies limits from external configuration. The default starter protects Servlet Filter and Spring MVC requests without requiring application annotations. Optional starters add Redis storage, outbound HTTP, database and messaging integrations, and Resilience4j behaviors.

Requires Java 25 and Spring Boot 4.1.2. The default token bucket is provided by Bucket4j.

## Modules

Import the BOM to align Rose module versions. Add only the integrations the application needs.

| Artifact | Purpose |
| --- | --- |
| `rose-ratelimit-bom` | Aligns Rose module versions |
| `rose-ratelimit-spring-boot-starter` | Servlet Filter and Spring MVC limits with local Bucket4j storage |
| `rose-ratelimit-spring-boot-starter-redis` | Redis-backed shared limits |
| `rose-ratelimit-spring-boot-starter-resilience4j` | Circuit breaker, retry, time limiter, and bulkhead |
| `rose-ratelimit-spring-boot-starter-restclient` | Synchronous Spring `RestClient` calls |
| `rose-ratelimit-spring-cloud-starter-openfeign` | OpenFeign calls; requires Spring Cloud OpenFeign |
| `rose-ratelimit-spring-boot-starter-mybatis` | MyBatis mapped statements |
| `rose-ratelimit-spring-boot-starter-alibaba-druid` | SQL table matching through Alibaba Druid |
| `rose-ratelimit-spring-boot-starter-mq` | Kafka and Rabbit listeners; requires the corresponding Spring messaging dependency |

The lower-level `rose-ratelimit-core`, `rose-ratelimit-bucket4j`, and `rose-ratelimit-spring-boot-autoconfigure` modules are available for custom integrations.

## Install

Import the BOM and add the default starter:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.zhijun</groupId>
            <artifactId>rose-ratelimit-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.zhijun</groupId>
        <artifactId>rose-ratelimit-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

The starter does not add a web server or MVC dependency. Use the web stack already selected by the application. For Redis, replace the default starter with `rose-ratelimit-spring-boot-starter-redis` and set `rose.ratelimit.store.type=redis`. Adding the Redis starter alone does not change the selected store.

## Configure HTTP limits

Rules are checked in configuration order. The first enabled rule matching a request is applied; an unmatched request passes through. Buckets start full and refill continuously.

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
      - id: items-read
        stage: MVC
        path: /api/items/**
        methods: [GET]
        capacity: 20
        refill-tokens: 20
        refill-period: 1s
        key-source: principal
```

`FILTER` covers Servlet requests, including endpoints handled by earlier filters when its order permits. `MVC` matches resolved controller mappings. Servlet paths use Spring's application path, not a substring of the raw request URI.

MVC rules with a path or handler are bound to registered controller methods during startup. By default, a rule that binds to no mapping fails startup. Late-added mappings, such as some documentation endpoints, require a restart to be included. MVC rules without a path or handler can be referenced by the optional `@RateLimit("rule-id")` annotation. This annotation applies only to controller methods or classes; it is not service AOP.

By default, MVC uses `scope: HANDLER`, with buckets keyed by controller class and method name. Overloaded methods with the same name share a bucket. Use `scope: RULE` to share one bucket across all matching handlers. Filter rules default to `scope: RULE`.

## Rule options

| Option | Meaning |
| --- | --- |
| `id` | Required and unique; letters, digits, `.`, `_`, and `-` |
| `stage` | `FILTER`, `MVC`, `REST_CLIENT`, `FEIGN`, `MYBATIS`, `JDBC`, `MQ`, or `INVOKE` |
| `path`, `methods` | Path pattern and optional HTTP methods for HTTP stages; empty methods match all |
| `target`, `handler` | Select an integration target or handler; formats vary by stage |
| `capacity`, `refill-tokens`, `refill-period` | Bucket size and refill rate; defaults are `10`, `10`, and `1s` |
| `key-source` | `ip`, `principal`, `header`, or a named resolver plugin |
| `key-header` | Header name when `key-source: header` |
| `missing-key` | `REJECT` returns 400; `SHARED` uses a shared missing-identity bucket |
| `store` | Store plugin name; defaults to the global `rose.ratelimit.store.type` (`local`) |
| `failure-mode` | `OPEN` passes on backend errors; `CLOSED` returns 503 |
| `status`, `content-type`, `body` | Rejection response for HTTP limits; defaults to 429 and Problem Details JSON |

`path` uses Spring `PathPattern`, not regular expressions. Enabled MVC rules require MVC on the classpath unless MVC is disabled. Different matching rules can both consume a request; deductions across buckets are not transactional, so an earlier deduction is not refunded if a later rule rejects. The same rule ID is deduplicated when it matches both Filter and MVC handling for one request.

## Optional integrations

### Redis storage

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
```

Redis requires `namespace` or `spring.application.name`; all application instances must use the same namespace. Different applications and environments should use different namespaces. The Redis backend uses Bucket4j CAS and a separately managed Lettuce connection. A backend failure is not retried for the current request; later requests can reconnect. Changing a rule's quota changes its bucket key and starts a new bucket. See [the v6 migration guide](docs/migration-v6.md) for the Redis key version change.

### RestClient

Use `stage: REST_CLIENT` with `target: rest-client:https://host` to match an origin, or use `path` to match paths across origins. The limit runs before the network request. Automatic integration applies to clients built from Spring Boot's injected `RestClient.Builder`; `RestClient.create()` bypasses the customizer.

### OpenFeign

Use `stage: FEIGN` and a target such as `feign:com.example.PaymentClient#createOrder`. The optional starter requires Spring Cloud OpenFeign. A rejected call throws `FeignException`; it is not returned as an HTTP 429 response.

### MyBatis

Use `stage: MYBATIS` with a mapped statement target, for example `mybatis:com.example.ReportMapper.findSummary`. Matching is by statement ID; the starter does not check whether the statement is read-only. A rejected call throws `SQLException`. Avoid applying a matching JDBC rule to the same query, since both stages consume quota.

### Druid JDBC

Use the Alibaba Druid starter and `stage: JDBC`, with a target such as `jdbc:sql-table:orders`. Matching is based on table names parsed from SQL. A rejected call throws `SQLException` with SQLState `45000`.

### Kafka and RabbitMQ

Use the MQ starter and add `spring-kafka`, `spring-boot-starter-amqp`, or both. Configure `stage: MQ` with a target such as `mq:kafka:topic` or `mq:rabbit:queue`. A rejected listener call throws `RateLimitMqRejectedException`.

### Resilience4j

The optional Resilience4j starter supports `kind: circuit-breaker` and `kind: composite`. Compose rate limiting and resilience behaviors on one rule:

```yaml
rose:
  ratelimit:
    rules:
      - id: fragile-api
        stage: FILTER
        path: /api/fragile/**
        kind: composite
        behaviors: [rate-limit, circuit-breaker]
        capacity: 100
        refill-period: 1s
        circuit-breaker:
          failure-rate-threshold: 50
          minimum-number-of-calls: 10
        circuit-open-status: 503
```

For HTTP requests, use `stage: FILTER` to decorate the Servlet chain with retry, time limiter, or bulkhead behaviors. `stage: MVC` applies rule selection to a controller but does not wrap its execution for these behaviors. Rate-limit rejection remains 429; an open circuit defaults to 503. Rejections do not trigger retry.

### Programmatic invocation

`stage: INVOKE` is used through the `RoseResilience` API, not an automatic method interceptor. It works in non-web applications as well. See the sample and [development guide](docs/development-prompt.md) for usage.

## Identity and failure behavior

- `ip` uses `remoteAddr`; forwarded headers are not parsed. Configure trusted proxy handling at the application edge.
- `principal` depends on authentication having established the principal. Place the Filter after authentication or use MVC where appropriate.
- Do not use an untrusted, client-controlled header as an identity key. Request bodies, passwords, and tokens are not read.
- Missing identity defaults to 400. Exhausted buckets return 429 and `Retry-After`.
- Backend failure mode defaults to `OPEN`; use `CLOSED` for rules that must fail closed. Program errors are not treated as backend failures.
- Local storage is per process and lost on restart. It defaults to at most 100,000 buckets and does not evict active buckets when full; adjust `rose.ratelimit.local.max-buckets` for the expected number of identities.

The Servlet Filter handles `REQUEST` and `ASYNC` dispatches. It skips `ERROR` dispatches and does not apply limits again to `FORWARD` or `INCLUDE` targets.

## Extend and observe

Custom stores implement `RateLimitStore`; custom identity sources implement `RateLimitKeyResolver`. Register either as a Spring bean with a unique `name`, then refer to that name from a rule. Missing or duplicate plugin names fail startup. A backend implementation should report operational failures using `RateLimitBackendException`.

When Micrometer is available, the starter records `rose.ratelimit.requests` with `rule`, `backend`, and `result` tags. Applications can also provide a `RateLimitObserver`; observer failures do not change quota decisions.

## Sample and build

The [sample application](rose-ratelimit-sample/README.md) demonstrates Filter and MVC rules, the optional annotation, MyBatis, and Resilience4j. Build and run it from the repository root:

```sh
./mvnw -pl rose-ratelimit-sample -am package
java -jar rose-ratelimit-sample/target/rose-ratelimit-sample-0.1.0-SNAPSHOT.jar
```

Run the repository checks with:

```sh
./mvnw spotless:check verify
```

Redis Testcontainers tests require Docker. JaCoCo generates the aggregate report at `rose-ratelimit-coverage/target/site/jacoco-aggregate/index.html`; the build enforces at least 80% aggregate line coverage.

## Scope and migration

WebFlux, business quota transactions, request-body identity extraction, and online rule refresh are not supported.

Version 6 replaces the framework integration previously supplied by [microsphere-resilience4j](https://github.com/microsphere-projects/microsphere-resilience4j). See [docs/migration-v6.md](docs/migration-v6.md) for migration steps and [CHANGELOG.md](CHANGELOG.md) for release notes.
