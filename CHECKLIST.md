# RelayGuard master checklist

Use checkboxes as the source of truth. Never mark a task complete without the stated evidence. Work in order and keep pull requests small.

## Phase 0 — Product validation
- [ ] Identify 30 target software agencies/SaaS companies.
- [ ] Send 10–15 tailored discovery messages.
- [ ] Conduct at least 5 discovery calls.
- [ ] Record current webhook failure workflow, frequency, business impact, and existing tools.
- [ ] Show a sample-data prototype.
- [ ] Ask for a paid pilot; record price objections.
- [ ] **Gate:** continue only with concrete evidence of a costly problem and credible purchase intent.

## Phase 1 — Repository foundation
- [x] Java 21 + Spring Boot project created.
- [x] Maven dependencies and configuration created.
- [x] PostgreSQL local service configured.
- [x] Flyway migration created.
- [x] GitHub Actions CI workflow added.
- [ ] Run `mvn clean verify` locally.
- [ ] Push repository and verify GitHub Actions is green.
- [ ] Add branch protection requiring CI before merge.

## Phase 2 — First vertical slice
- [x] `POST /api/v1/events` accepts a valid event.
- [x] Event is persisted and returns `202 Accepted` with an ID.
- [x] `GET /api/v1/events/{id}` returns event metadata.
- [x] Shared development API key filter added.
- [x] Automated tests cover successful ingestion, missing/wrong API key, validation, and unknown event.
- [ ] Verify tests pass in a real Maven environment.
- [ ] Add payload size limits and consistent error response schema.
- [ ] Add idempotency key semantics and tests before external retry support.

## Phase 3 — Identity and tenancy
- [ ] Define workspace, membership, project, and project credential schema.
- [ ] Store credential hashes, never raw credentials.
- [ ] Support credential rotation and revocation.
- [ ] Enforce ownership checks on every project-owned resource.
- [ ] Add tests proving cross-workspace reads/writes are rejected.
- [ ] Remove shared static API key from public/pilot configuration.

## Phase 4 — Destinations and delivery
- [ ] Add destination CRUD with ownership checks.
- [ ] Validate destination URL scheme and hostname.
- [ ] Block loopback, private, link-local, multicast, and metadata-service destinations.
- [ ] Revalidate DNS resolution at connection time; account for DNS rebinding.
- [ ] Restrict redirects and set strict connection/read timeouts.
- [ ] Implement durable asynchronous delivery jobs.
- [ ] Record each attempt: timestamp, status, duration, and sanitized error.
- [ ] Add tests for 2xx, 4xx, 5xx, timeout, DNS failure, and unsafe destination rejection.

## Phase 5 — Retries and replay
- [ ] Implement bounded exponential backoff with jitter.
- [ ] Persist retry schedule so restarts do not lose jobs.
- [ ] Add max attempts and exhausted/dead-letter state.
- [ ] Make job processing safe under duplicate execution.
- [ ] Implement manual replay without deleting previous attempts.
- [ ] Audit replay actions.
- [ ] Test duplicate deliveries and idempotency behavior.

## Phase 6 — Dashboard and notifications
- [ ] Add event list, filters, pagination, and event detail view.
- [ ] Add delivery attempt timeline.
- [ ] Add failure threshold configuration.
- [ ] Add email notifications without blocking delivery workers.
- [ ] Add alert deduplication and tests.

## Phase 7 — Reliability and release
- [ ] Add rate limits per project/workspace.
- [ ] Define payload retention, deletion, and redaction policies.
- [ ] Ensure secrets and sensitive payloads are excluded from logs.
- [ ] Add integration tests against PostgreSQL (e.g. Testcontainers) in CI.
- [ ] Add dependency vulnerability scanning and secret scanning.
- [ ] Test database backup and restore procedure.
- [ ] Add health/readiness and basic operational metrics.
- [ ] Run load tests against an agreed pilot target.
- [ ] Complete security review and human review of all critical findings.
- [ ] Publish setup, limitations, support, and incident-response documentation.
- [ ] Run a controlled pilot with explicit customer consent and retention limits.

## Definition of done — every feature
- [ ] Acceptance criteria are documented.
- [ ] Code is reviewed; no unrelated changes are included.
- [ ] Unit/integration tests added for behavior and edge cases.
- [ ] `mvn clean verify` passes.
- [ ] Security and tenant-isolation implications reviewed.
- [ ] API/documentation updated.
- [ ] Human approves merge.

## Stop/go gates
- **Day 14:** validate pain and willingness to pay before investing in broad implementation.
- **Day 30:** complete one end-to-end event workflow in a test environment.
- **Day 60:** run a controlled pilot and ask for payment.
- **Day 90:** continue only if usage, payment, or strong renewal intent supports it.
