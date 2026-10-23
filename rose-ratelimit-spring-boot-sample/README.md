# Rate Limit Sample

Runnable Spring MVC application showing configuration-based request limits. The login and item endpoints have no rate-limit annotations; `/api/export` shows the optional `@RateLimit` reference.

## Run

From the repository root:

```sh
mvn -pl rose-ratelimit-spring-boot-sample -am package
java -jar rose-ratelimit-spring-boot-sample/target/rose-ratelimit-spring-boot-sample-0.1.0-SNAPSHOT.jar
```

The sample uses the local in-memory store and starts on port 8080.

## Try it

Each command below consumes a token from the matching rule:

```sh
curl -i -X POST http://localhost:8080/api/auth/login
curl -i http://localhost:8080/api/items
curl -i -X POST http://localhost:8080/api/items
curl -i http://localhost:8080/api/export
```

The login Filter rule allows three requests per minute per remote IP. The item routes show MVC matching by path and HTTP method. The export route uses the optional annotation to reference its externally configured rule. Once a rule's capacity is exhausted, the response is `429` with `Retry-After`.

This sample endpoint does not authenticate users or process credentials. For real login limits, choose a key source and Filter order that match the application's authentication pipeline.
