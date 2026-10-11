# RelayGuard — Webhook Reliability Platform

A Spring Boot starter for the RelayGuard MVP. This repository currently implements the **first vertical slice**: authenticated event ingestion, durable event persistence, event lookup, database migration, and automated API tests. It is a development starter, **not production-ready webhook infrastructure** yet.

## Tech stack

- Java 21
- Spring Boot 3.5.6
- Spring Web, Validation, Data JPA, Actuator
- PostgreSQL + Flyway
- H2 for fast automated tests
- Maven
- GitHub Actions CI

## Requirements

- JDK 21
- Maven 3.9+
- Docker with Docker Compose (for PostgreSQL/local full stack)

## Run automated tests

```bash
mvn clean verify
```

The tests use an in-memory H2 database and do not require Docker.

## Run locally

1. Start PostgreSQL:

   ```bash
   docker compose up -d postgres
   ```

2. Start the app:

   ```bash
   export RELAYGUARD_API_KEY='local-dev-key-change-me'
   mvn spring-boot:run
   ```

   Default local database settings are in `src/main/resources/application.yml`.

3. Submit an event:

   ```bash
   curl -i http://localhost:8080/api/v1/events \
     -H 'Content-Type: application/json' \
     -H 'X-API-Key: local-dev-key-change-me' \
     -d '{"eventType":"order.created","payload":{"orderId":"ord_123"}}'
   ```

4. Use the returned `id` to inspect the event:

   ```bash
   curl http://localhost:8080/api/v1/events/REPLACE_WITH_EVENT_UUID \
     -H 'X-API-Key: local-dev-key-change-me'
   ```

5. Health check: `GET http://localhost:8080/actuator/health`.

For the full container stack, use `docker compose up --build`.

## Important security notice

`RELAYGUARD_API_KEY` is a single shared development key to bootstrap the first vertical slice. It is **not** the final per-project credential model. Do not expose this configuration to the public internet or use real customer data. Before a public pilot, implement hashed, project-scoped, revocable credentials; tenant isolation; rate limiting; request-size limits; payload retention/deletion; and SSRF-safe outbound delivery validation.

## Product status

- [x] Initial Spring Boot project
- [x] PostgreSQL configuration and Flyway migration
- [x] Authenticated event ingestion endpoint
- [x] Event lookup endpoint
- [x] CI workflow running `mvn clean verify`
- [x] Automated tests for acceptance, missing/wrong key, validation, and not-found behavior
- [ ] Project/workspace model and project-scoped credentials
- [ ] Webhook destinations
- [ ] Durable asynchronous delivery worker
- [ ] Delivery attempt history
- [ ] Retry scheduler and dead-letter state
- [ ] Safe manual replay
- [ ] Rate limiting and payload-size limits
- [ ] SSRF protections
- [ ] Dashboard
- [ ] Email notifications
- [ ] Production deployment and backup/recovery runbook

Use `CHECKLIST.md` as the master tracker and `AGENT_WORKFLOW.md` to direct coding agents.
