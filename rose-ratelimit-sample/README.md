# Rate Limit Sample

Runnable Spring MVC application showing configuration-based request limits. The login and item endpoints have no rate-limit annotations; `/api/export` shows the optional `@RateLimit` reference.

## Run

From the repository root:

```sh
./mvnw -pl rose-ratelimit-sample -am package
java -jar rose-ratelimit-sample/target/rose-ratelimit-sample-0.1.0-SNAPSHOT.jar
```

The sample uses the local in-memory store and starts on port 8080.
It also uses an in-memory H2 database with HikariCP and a MyBatis mapper for the read-query limit example.

## Try it

Each command below consumes a token from the matching rule:

```sh
curl -i -X POST http://localhost:8080/api/auth/login
curl -i http://localhost:8080/api/items
curl -i -X POST http://localhost:8080/api/items
curl -i http://localhost:8080/api/export
curl -i http://localhost:8080/api/reports/count
```

The login Filter rule allows three requests per minute per remote IP. The item routes show MVC matching by path and HTTP method. The export route uses the optional annotation to reference its externally configured rule. Once a rule's capacity is exhausted, the response is `429` with `Retry-After`.

The report endpoint runs `ReportMapper.countRows`, limited to three calls per minute by its MyBatis mapped statement ID. Further calls fail with the MyBatis rate-limit `SQLException`. The sample rule targets a read-only `SELECT`; the starter does not currently validate that a mapped statement is read-only. Do not add a matching JDBC rule for this same statement, or the call will be charged at both stages.

## Resilience4j (v6)

The sample depends on `rose-ratelimit-spring-boot-starter-resilience4j`.

**Circuit breaker (MVC)** — rule `fragile-cb` on `/api/fragile/**`. Successful calls:

```sh
curl -s 'http://localhost:8080/api/fragile/ping'
```

Simulate failures until the circuit opens (then **503**, distinct from rate-limit **429**):

```sh
curl -i 'http://localhost:8080/api/fragile/ping?fail=true'
```

**Retry (FILTER)** — rule `demo-retry` on `/api/demo/retry` with `behaviors: [rate-limit, retry]`. The handler fails twice then succeeds; one client call returns 200 after Resilience4j retries (still one rate-limit decision per request).

```sh
curl -s http://localhost:8080/api/demo/retry
# {"status":"ok","attempts":3}
```

**Bulkhead (FILTER)** — rule `demo-bulkhead` on `/api/demo/**`. Retry, time-limiter, and bulkhead on HTTP must use **`stage: FILTER`** (same path) so the Servlet chain is decorated after rate-limit allow; **`stage: MVC` alone does not wrap controllers** (startup WARN if configured anyway).

```sh
curl -i http://localhost:8080/api/demo/work
```

This sample endpoint does not authenticate users or process credentials. For real login limits, choose a key source and Filter order that match the application's authentication pipeline.
