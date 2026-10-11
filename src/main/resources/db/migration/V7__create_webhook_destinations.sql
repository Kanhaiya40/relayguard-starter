CREATE TABLE webhook_destinations (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    target_url VARCHAR(2048) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_destination_project_url UNIQUE (project_id, target_url)
);
CREATE INDEX idx_webhook_destinations_project_enabled
    ON webhook_destinations (project_id, enabled, created_at DESC);
