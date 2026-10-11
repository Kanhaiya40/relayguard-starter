CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_webhook_events_received_at ON webhook_events (received_at DESC);
