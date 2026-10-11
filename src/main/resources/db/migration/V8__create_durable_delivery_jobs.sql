CREATE TABLE delivery_jobs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES webhook_events(id),
    destination_id UUID NOT NULL REFERENCES webhook_destinations(id),
    state VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_http_status INTEGER,
    last_error_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_delivery_job_event_destination UNIQUE (event_id, destination_id),
    CONSTRAINT ck_delivery_job_state CHECK (state IN ('PENDING', 'IN_PROGRESS', 'DELIVERED', 'EXHAUSTED'))
);
CREATE INDEX idx_delivery_jobs_claim ON delivery_jobs (state, next_attempt_at, lease_until);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES delivery_jobs(id),
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    http_status INTEGER,
    duration_ms BIGINT,
    outcome VARCHAR(30) NOT NULL,
    error_code VARCHAR(50),
    CONSTRAINT uq_delivery_attempt_number UNIQUE (job_id, attempt_number)
);
CREATE INDEX idx_delivery_attempts_job ON delivery_attempts (job_id, attempt_number);
