CREATE TABLE user_access_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    key_prefix VARCHAR(24) NOT NULL UNIQUE,
    credential_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_user_access_credentials_user ON user_access_credentials (user_id, revoked_at);

ALTER TABLE webhook_events
    ADD COLUMN project_id UUID REFERENCES projects(id);

DROP INDEX idx_webhook_events_idempotency_key;
CREATE UNIQUE INDEX uq_webhook_event_project_idempotency
    ON webhook_events (project_id, idempotency_key);
CREATE INDEX idx_webhook_events_project_received
    ON webhook_events (project_id, received_at DESC);
