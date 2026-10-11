# RelayGuard — Webhook Reliability Platform

RelayGuard is a webhook-reliability pilot product. It accepts project-scoped events, persists them with idempotency, queues deliveries for configured destinations, records attempts, retries transient failures, and lets authorized workspace members inspect, replay, and delete events. It is suitable for a controlled pilot only after the operator configures production infrastructure, data handling, backups, and monitoring. It is not a general-purpose production service or a substitute for receiver-side idempotency.

## Stack and requirements

- Java 21 and Maven 3.9+
- Spring Boot 3.5.6
- PostgreSQL 16+ with Flyway migrations
- Docker Compose for local PostgreSQL

## Verify

```bash
./mvnw clean verify
```

Fast API tests use H2. `PostgresDeliveryQueueIntegrationTest` uses Testcontainers to apply the real Flyway migrations and exercise PostgreSQL's delivery queue SQL; it runs when Docker is available.

## Run locally

Start PostgreSQL and the application:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Open [http://localhost:8080/](http://localhost:8080/) for the lightweight dashboard. User access tokens and project keys stay in the current tab's memory; sign in again after refreshing or closing it.

Local database defaults are in `src/main/resources/application.yml`; they are for development only. The worker is enabled by default. To run only the API locally, set `RELAYGUARD_DELIVERY_ENABLED=false`.

## Create an account and project

Register an account. This creates an owner membership and workspace and returns a bearer token. Passwords must be 12–72 UTF-8 bytes.

```bash
curl -sS http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"owner@example.test","password":"correct-horse-battery-staple","workspaceName":"Example workspace"}'
```

Use the returned `accessToken` and `workspaceId` to create a project:

```bash
curl -sS http://localhost:8080/api/v1/workspaces/REPLACE_WITH_WORKSPACE_ID/projects \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"name":"production"}'
```

Issue a project credential. Its raw value is returned once; RelayGuard stores only its SHA-256 hash. Rotation issues a replacement while the old key remains valid, so revoke the old credential after updating the client.

```bash
curl -sS -X POST http://localhost:8080/api/v1/projects/REPLACE_WITH_PROJECT_ID/credentials \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN'
```

## Configure a destination

Destination management uses the user bearer token. The destination must be a public HTTP(S) URL. Private, loopback, link-local, multicast, metadata, and other non-public addresses are rejected. DNS is checked again by the delivery transport when it connects, redirects are disabled, and network timeouts are bounded.

```bash
curl -sS http://localhost:8080/api/v1/projects/REPLACE_WITH_PROJECT_ID/destinations \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://receiver.example.test/webhooks"}'
```

Each destination starts with `RELAYGUARD_DELIVERY_MAX_ATTEMPTS`, `RELAYGUARD_DELIVERY_BASE_DELAY_MS`, and `RELAYGUARD_DELIVERY_MAX_DELAY_MS` defaults. Set a destination-specific policy with the owner-only endpoint:

```bash
curl -sS -X PATCH http://localhost:8080/api/v1/projects/REPLACE_WITH_PROJECT_ID/destinations/REPLACE_WITH_DESTINATION_ID/retry-policy \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"retryMaxAttempts":5,"retryBaseDelayMs":2000,"retryMaxDelayMs":120000}'
```

## Configure email failure alerts

The operator must configure SMTP and enable `RELAYGUARD_EMAIL_NOTIFICATIONS_ENABLED=true` before project owners can turn failure alerts on. Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `RELAYGUARD_EMAIL_FROM` from the hosting secret manager. SMTP connect/read/write timeouts default to 5 seconds. Notification workers use their own scheduler thread and retry failed sends up to eight times; webhook delivery does not wait for email. The same SMTP configuration delivers workspace quota warnings.

Project owners can configure an alert recipient, failure threshold, rolling count window (1–1440 minutes), and repeat-alert cooldown (1–10080 minutes) in the dashboard or API. A deduplication key allows at most one alert per project, destination, and cooldown time bucket. Email contains the project, event ID, destination host, failure count, and a sanitized failure code. It never includes event payloads or credentials. Pending notifications are removed when their event is deleted or expires.

```bash
curl -i -X PUT http://localhost:8080/api/v1/projects/REPLACE_WITH_PROJECT_ID/notification-settings \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"enabled":true,"recipientEmail":"ops@example.test","failureThreshold":3,"windowMinutes":10,"cooldownMinutes":60}'
```

## Ingest and retrieve an event

Use the project credential as `X-API-Key`:

```bash
curl -i http://localhost:8080/api/v1/events \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: REPLACE_WITH_PROJECT_CREDENTIAL' \
  -H 'Idempotency-Key: order-123' \
  -d '{"eventType":"order.created","payload":{"orderId":"ord_123"}}'
```

The API persists the event and delivery jobs before returning `202 Accepted` and an event ID. Retrieve the event and its delivery history with the same project credential:

```bash
curl http://localhost:8080/api/v1/events/REPLACE_WITH_EVENT_UUID \
  -H 'X-API-Key: REPLACE_WITH_PROJECT_CREDENTIAL'

curl http://localhost:8080/api/v1/events/REPLACE_WITH_EVENT_UUID/deliveries \
  -H 'X-API-Key: REPLACE_WITH_PROJECT_CREDENTIAL'
```

The event list accepts optional `eventType`, `from`, `to`, `destinationId`, and `deliveryStatus` filters plus `page` and `size`. `deliveryStatus` is one of `PENDING`, `IN_PROGRESS`, `DELIVERED`, or `EXHAUSTED`. Destination IDs are validated against the authenticated project. The dashboard exposes these filters and keeps results paginated.

Repeated ingestion idempotency keys with the same event type and payload return the original event. Reusing a key with a different request returns `409 Conflict`. Keys are scoped to the project. Request bodies are limited to 1 MiB by default; set `RELAYGUARD_MAX_REQUEST_BYTES` to change the limit. API errors use `code`, `message`, `status`, and `timestamp` fields.

## Pilot usage limits

The pilot plan accepts up to 100,000 new events per workspace per UTC calendar month across all its projects. Idempotent retries of an already accepted event do not consume another event. At the limit, ingestion returns `429 WORKSPACE_EVENT_QUOTA_EXCEEDED` with a `Retry-After` header and the next UTC reset time in the error message. Senders should retry with the same idempotency key after the reset. At 80% usage, RelayGuard queues one email warning for the workspace owner; the operator must configure SMTP as described above. Owners and members can check usage and limits with:

```bash
curl http://localhost:8080/api/v1/workspaces/REPLACE_WITH_WORKSPACE_ID/usage \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN'
```

The pilot allows 10 enabled destinations per project. Disabling one frees a slot. Ingestion request bodies are limited to 1 MiB by default, including the JSON envelope, and new events are retained for 30 days by default. The event allowance can be changed by the operator with `RELAYGUARD_EVENTS_PER_WORKSPACE_MONTH`; avoid raising it beyond the deployment's tested capacity.

## Replay an event

Replay uses the user bearer token, checks project membership, and requires an `Idempotency-Key`. Each accepted replay creates new jobs for currently enabled destinations; previous jobs and attempts remain intact. Reusing the same key returns the existing replay instead of creating duplicate jobs. Replay actions appear in the event's audit endpoint. Project audit history (`GET /api/v1/projects/{projectId}/audit`) also records project creation, destination and notification settings changes, and project credential issue/revoke actions; only the latest 100 records are returned.

```bash
curl -i -X POST http://localhost:8080/api/v1/projects/REPLACE_WITH_PROJECT_ID/events/REPLACE_WITH_EVENT_UUID/replays \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' \
  -H 'Idempotency-Key: replay-123'

curl http://localhost:8080/api/v1/projects/REPLACE_WITH_PROJECT_ID/events/REPLACE_WITH_EVENT_UUID/audit \
  -H 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN'
```

Retry policy is configured per destination: maximum attempts (1–25), base delay, and capped maximum delay (up to 24 hours). These values are persisted with the destination and each delivery job uses its destination's current policy. Changes affect retries that have not yet been exhausted. Exhausted jobs remain visible in delivery history. Delivery is at least once: receiver endpoints should use the event ID for their own deduplication. RelayGuard cannot guarantee exactly-once processing by a receiver.

Event ingestion is limited to 600 requests per project per minute by default. Configure the server-wide limit with `RELAYGUARD_EVENTS_PER_MINUTE`; a rejected request receives `429 Too Many Requests` and a `Retry-After` header. This is an operational safeguard, not a billing or per-customer plan system.

## Payload retention and deletion

New events are retained for 30 days by default. Set `RELAYGUARD_RETENTION_DAYS` to a value of at least 1 to change the retention period for newly accepted events. A scheduled cleanup removes expired events together with their delivery jobs and attempt history in batches of up to 100; change the interval with `RELAYGUARD_RETENTION_CLEANUP_INTERVAL_MS` (default 3,600,000 ms). An authorized project member can permanently delete an event and its delivery history with `DELETE /api/v1/projects/{projectId}/events/{eventId}`. The action is recorded in project audit history. Deletion is rejected while any delivery for that event is in progress. Existing events are backfilled to expire 30 days after receipt when the retention migration is applied.

## Authentication, health, and release status

Log in at `POST /api/v1/auth/login` to issue another user access token. Revoke a user token at `POST /api/v1/auth/tokens/{credentialId}/revoke`. Revoke a project key at `POST /api/v1/projects/{projectId}/credentials/{credentialId}/revoke`. Workspace and project management calls use bearer tokens and verify membership; event ingestion and lookup use a project key. Operational endpoints are `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/metrics`. Metrics use bounded labels and do not include project or workspace IDs.

Passwords use BCrypt. User access tokens expire after 30 days and can be revoked; project credentials are random secrets and only hashes are stored. Use HTTPS in deployed environments. The current Compose database password is only for local development.

Remaining work before a paid, general-availability launch includes accessibility review for the dashboard, billing and plan management, payload redaction, production deployment hardening, backup/restore drills, load testing, and an independent security review. Do not use real customer payloads or credentials until production data handling and security controls have been reviewed. A controlled pilot must have an explicit customer agreement and operator-managed retention limits.

See `CHECKLIST.md` for the master tracker and `AGENT_WORKFLOW.md` for the engineering workflow.

Pilot operators must read [`docs/OPERATIONS.md`](docs/OPERATIONS.md) before accepting customer data. Vulnerability reporting instructions are in [`SECURITY.md`](SECURITY.md).
