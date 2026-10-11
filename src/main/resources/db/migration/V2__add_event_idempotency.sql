ALTER TABLE webhook_events
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN request_fingerprint CHAR(64);

CREATE UNIQUE INDEX idx_webhook_events_idempotency_key
    ON webhook_events (idempotency_key);
