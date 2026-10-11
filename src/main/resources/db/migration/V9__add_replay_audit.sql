ALTER TABLE delivery_jobs DROP CONSTRAINT uq_delivery_job_event_destination;
ALTER TABLE delivery_jobs ADD COLUMN replay_audit_event_id UUID;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    actor_user_id UUID NOT NULL REFERENCES app_users(id),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(60) NOT NULL,
    target_id UUID NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_audit_project_action_request UNIQUE (project_id, action, request_key)
);

ALTER TABLE delivery_jobs ADD CONSTRAINT fk_delivery_job_replay_audit
    FOREIGN KEY (replay_audit_event_id) REFERENCES audit_events(id);
CREATE INDEX idx_audit_events_project_target_created
    ON audit_events (project_id, target_id, created_at DESC);
CREATE INDEX idx_delivery_jobs_replay_audit
    ON delivery_jobs (replay_audit_event_id);
