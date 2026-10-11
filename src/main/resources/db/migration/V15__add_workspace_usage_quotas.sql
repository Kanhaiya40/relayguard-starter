CREATE TABLE workspace_monthly_usage (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    accepted_events INTEGER NOT NULL CHECK (accepted_events >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_workspace_monthly_usage_period UNIQUE (workspace_id, period_start)
);

CREATE TABLE workspace_quota_alert_outbox (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    usage_count INTEGER NOT NULL CHECK (usage_count >= 1),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_workspace_quota_alert_period UNIQUE (workspace_id, period_start)
);

CREATE INDEX idx_workspace_quota_alert_claim
    ON workspace_quota_alert_outbox (status, available_at, created_at);
