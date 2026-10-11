CREATE TABLE project_notification_settings (
    project_id UUID PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    failure_threshold INTEGER NOT NULL CHECK (failure_threshold BETWEEN 1 AND 100),
    window_minutes INTEGER NOT NULL CHECK (window_minutes BETWEEN 1 AND 1440),
    cooldown_minutes INTEGER NOT NULL CHECK (cooldown_minutes BETWEEN 1 AND 10080),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE email_notification_outbox (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES webhook_events(id) ON DELETE CASCADE,
    destination_id UUID NOT NULL REFERENCES webhook_destinations(id) ON DELETE CASCADE,
    recipient_email VARCHAR(320) NOT NULL,
    failure_count INTEGER NOT NULL CHECK (failure_count >= 1),
    failure_code VARCHAR(50),
    deduplication_key VARCHAR(160) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(50)
);

CREATE INDEX idx_email_notification_outbox_claim
    ON email_notification_outbox (status, available_at, lease_until, created_at);
