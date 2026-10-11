CREATE TABLE project_rate_limit_counters (
    project_id UUID PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 0)
);
