# AegisFlow

A Java 17 gateway project using Netty for HTTP ingress, Nacos for service discovery and dynamic rules, and AsyncHttpClient for asynchronous forwarding.

## Features

- Ordered filters for routing, load balancing and optional authentication, mock, gray routing and rate limiting.
- Logical request deadlines, bounded GET/HEAD retries and downstream cancellation on client disconnect. POST is not automatically retried.
- Bounded request buffering and local per-service in-flight admission control.
- Rule validation and atomic publication of routing-index snapshots.
- Request correlation and Micrometer/Prometheus metrics.
- Optional RabbitMQ access events, bounded publishing, manual consumer acknowledgements and idempotent MySQL writes.
- Shutdown admission gate, bounded in-flight waiting and resource cleanup.

```text
HTTP client → Netty → request processor → filters → downstream HTTP service
                                      └→ access event → RabbitMQ → consumer → MySQL
```

The repository contains application source, tests, Maven configuration and minimal runtime examples. Personal files, learning materials, screenshots, logs, build outputs and local benchmark data are excluded.

## Modules

| Module | Responsibility |
|---|---|
| BlossomGateway-Core | Netty ingress, filters, routing and lifecycle |
| BlossomGateway-Common | Shared configuration and utilities |
| BlossomGateway-Client | Backend annotation scanning and registration integration |
| BlossomGateway-Register-Center | Registration interfaces and Nacos implementation |
| BlossomGateway-Config-Center | Configuration interfaces and Nacos implementation |
| BlossomGateway-Http | Spring Boot HTTP demo service |
| BlossomGateway-Access | Versioned access events and RabbitMQ publisher |
| BlossomGateway-Access-Consumer | Event consumer and JDBC persistence |
| BlossomGateway-Dubbo / BlossomRPC-* | Inherited experimental protocol modules |

Existing Java package names and Maven module identifiers are retained for compatibility. The repository name and project display name are AegisFlow.

## Build

Requirements: JDK 17 and Maven 3.8+.

```shell
mvn clean test
mvn install
```

Tests do not require live infrastructure. Integration execution requires separately provisioned Nacos, Redis and HTTP backend services; the optional access pipeline additionally requires RabbitMQ and MySQL. No production credentials are bundled.

## HTTP demo

1. Provision Nacos at the configured address; the default is loopback port 8848.
2. Publish `api-gateway.json` using Nacos Data ID `api-gateway` and Group matching `env`; default `dev`.
3. Configure backend `api.registerAddress` and `api.env`, then launch `blossom.project.httpserver.Application` from `BlossomGateway-Http`.
4. Configure Core `gateway.properties` or startup options, then launch `blossom.project.core.Bootstrap`. Default gateway port: 8888.
5. Send the request with the service/version header:

```shell
curl -H "uniqueId: backend-http-server:1.0.0" http://127.0.0.1:8888/http-server/ping
```

Launch the main classes through an IDE configured from the Maven project, or use a runtime classpath containing the relevant module and its dependencies. The examples are HTTP-only; visiting the URL without its required header is not an equivalent request.

## External configuration

| Environment variable | Purpose |
|---|---|
| `AEGIS_JWT_SECRET` | Base64-encoded signing key for the JWT example filter; use a randomly generated key and never commit it |
| `AEGIS_RABBIT_USER` | RabbitMQ username, required when access events are enabled |
| `AEGIS_RABBIT_PASSWORD` | RabbitMQ password, required when access events are enabled |
| `AEGIS_RABBIT_HOST` | Consumer RabbitMQ host, default loopback |
| `AEGIS_DB_URL` | Consumer JDBC URL; default loopback MySQL port 3306 and database `aegisflow_access` |
| `AEGIS_DB_USER` | Required consumer database username |
| `AEGIS_DB_PASSWORD` | Required consumer database password |

Access events are disabled by default. Enable Core with `--accessEventsEnabled=true` after configuring broker credentials and address. Core broker host/port can be overridden by `--rabbitHost=...` and `--rabbitPort=...`.

For the consumer, provision the database and execute `BlossomGateway-Access-Consumer/src/main/resources/schema.sql`. Launch `blossom.project.access.consumer.ConsumerMain` with the required environment. Consumer JVM properties `access.dbUrl`, `access.dbUser`, `access.dbPassword`, `access.rabbitHost`, `access.rabbitPort`, `access.rabbitUser` and `access.rabbitPassword` provide overrides.

Metrics: Core `/prometheus` on default loopback port 18000. Consumer `/stats` and `/prometheus` on default loopback port 8094. Keep these unauthenticated demo endpoints off public networks. Use external secret management for deployment; do not put credentials in checked-in files or shell history.

## Limitations

This is an educational implementation, not a production-certified gateway. Access events can be lost on memory-buffer overflow, exhausted publishing attempts or forced process termination. Classic retry/dead-letter forwarding is not an end-to-end zero-loss guarantee. Unique event IDs make database writes idempotent, not exactly-once processing.

Rule validation covers common structural errors, not every filter's semantics. Filter-chain cache invalidation on strategy-composition changes needs further work. Last-known-good rules are memory-only. Local admission limits are not global cluster quotas. Shutdown budgets are not a hard deadline for the entire JVM or arbitrary blocking code. Experimental RPC paths have not received equivalent HTTP integration coverage.

There is no claim of stable maximum QPS. Performance depends on the workload, deployment, enabled features and correctness criteria. Dependency upgrades and production security hardening remain separate work.

## Origin and permissions

AegisFlow is derived from [BlossomGateway](https://github.com/ZhangBlossom/BlossomGateway), with extensions around request reliability, admission control, access events, rule updates and lifecycle verification. Thanks to the upstream project for the foundational implementation.

The original implementation is not claimed as wholly original work. No new license is asserted for upstream code. An explicit upstream redistribution license was not identified during preparation; users should verify applicable permissions before redistribution or commercial use. Attribution is not a substitute for permission.
