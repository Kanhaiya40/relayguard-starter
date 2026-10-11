# Pilot operations runbook

This runbook covers a single-region, single-application pilot deployment. It is not a high-availability or compliance certification. The person operating a pilot must own the database, encryption keys, backups, access control, and customer incident communication.

## Before accepting customer data

1. Run RelayGuard behind a TLS-terminating reverse proxy. Do not expose PostgreSQL publicly. The production profile exposes health and info only; if metrics are needed, expose them on an operator-only network. Expose health probes only as required by the platform.
2. Activate Spring's `production` profile with `SPRING_PROFILES_ACTIVE=production`. Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` from the hosting platform's secret manager. The production profile requires these values and does not expose actuator metrics. Never use Compose's local development password in a hosted environment.
3. For email alerts, set `RELAYGUARD_EMAIL_NOTIFICATIONS_ENABLED=true` and configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `RELAYGUARD_EMAIL_FROM` through the secret manager. Verify delivery with a test mailbox before onboarding. Keep SMTP timeouts bounded; notification workers run independently from webhook delivery. Project alert addresses and workspace owner addresses are stored with notification records, so protect the database, backups, and operator access accordingly.
4. Set and record `RELAYGUARD_RETENTION_DAYS`, `RELAYGUARD_MAX_REQUEST_BYTES`, `RELAYGUARD_EVENTS_PER_MINUTE`, `RELAYGUARD_EVENTS_PER_WORKSPACE_MONTH`, and default delivery retry settings. The pilot defaults are 30 days retention, 1 MiB request size, 600 requests per project per minute, 100,000 accepted events per workspace per UTC month, and 10 enabled destinations per project. Agree on retention and quota behavior with each pilot customer before onboarding.
5. Use a dedicated PostgreSQL database with encrypted storage and TLS connections. Restrict database network access to the application and backup operator.
6. Enable GitHub Dependabot alerts, secret scanning and push protection, and CodeQL for the repository. Review findings before each release.
7. Make a backup and complete the restore drill below before creating the first customer project. Repeat the drill quarterly and after material schema changes.
8. Confirm a named operator can view health/metrics, revoke a project credential, disable a destination, and communicate an incident to the pilot customer.

## Health and first response

- `GET /actuator/health/liveness` indicates whether the process is alive.
- `GET /actuator/health/readiness` includes the database readiness check.
- `GET /actuator/metrics` is an operator endpoint and must be protected at the network layer.
- Check database availability, worker logs, queue age, retry state, and delivery-attempt history before restarting services. Do not log raw API keys or event payloads while diagnosing incidents.
- For a suspected credential leak, revoke the affected user token or project credential, issue a replacement, and notify the affected customer through the contact channel in the pilot agreement.
- For accidental payload exposure, restrict access first, preserve relevant audit records, determine affected project IDs and time range, then follow the notification terms in the pilot agreement. Avoid copying payloads into tickets or chat.
- Record incident start/end time, affected projects, customer impact, mitigation, and follow-up actions in the operator's incident log.

## PostgreSQL backup and restore drill

Use a protected backup directory on storage encrypted at rest. Set `PGHOST`, `PGPORT`, `PGDATABASE`, and `PGUSER` from the operator's secret manager and configure `.pgpass` (mode `0600`) for authentication; do not put passwords in shell history. `DATABASE_URL` for Spring Boot is a JDBC URL and must not be passed directly to PostgreSQL command-line tools.

```bash
umask 077
mkdir -p backups
pg_dump --format=custom --file="backups/relayguard-$(date -u +%Y%m%dT%H%M%SZ).dump" --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname="$PGDATABASE"
pg_restore --list backups/relayguard-YYYYMMDDTHHMMSSZ.dump >/dev/null
```

For a restore drill, create a new, empty, isolated database. Never restore over the active pilot database as a test:

```bash
createdb --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" relayguard_restore_drill
pg_restore --exit-on-error --no-owner --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname=relayguard_restore_drill backups/relayguard-YYYYMMDDTHHMMSSZ.dump
psql --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname=relayguard_restore_drill --command='SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1'
psql --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --dbname=relayguard_restore_drill --command='SELECT count(*) FROM webhook_events'
dropdb --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" relayguard_restore_drill
```

Record the backup timestamp, restore completion time, Flyway version, and event/job/attempt counts in the operator run log. A successful `pg_restore` alone is not proof the application can use the restored database: before the pilot, start a temporary RelayGuard instance against the restored database and check readiness plus a read-only dashboard/API request. Keep backup retention aligned with the customer agreement because backups may contain deleted or expired payloads until the backup itself expires.

## Release and rollback

1. Require green CI and CodeQL for the exact commit. Review migrations and retry/retention configuration changes.
2. Back up PostgreSQL before release. Apply schema migrations using the normal application startup path.
3. Verify liveness, readiness, project sign-in, a synthetic event, and delivery history using synthetic data.
4. If application code must be rolled back, verify database compatibility first. Flyway migrations are forward-only; do not manually reverse a migration on a database with customer data. Restore a backup only under the agreed recovery procedure.

## Pilot boundaries

RelayGuard provides configurable pilot event quotas and queued email alerts, but not billing, plan management, multi-region availability, a published support SLA, or compliance certification. Verify SMTP delivery before relying on quota warnings. Treat pilot traffic as best-effort and agree on response hours, retention, data categories, and incident contact details in writing. Do not promise exactly-once delivery; destination handlers must deduplicate using the RelayGuard event ID.
