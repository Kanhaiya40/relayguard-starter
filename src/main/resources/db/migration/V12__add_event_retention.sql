ALTER TABLE webhook_events ADD COLUMN retention_until TIMESTAMP WITH TIME ZONE;
UPDATE webhook_events SET retention_until = received_at + INTERVAL '30 days';
ALTER TABLE webhook_events ALTER COLUMN retention_until SET NOT NULL;
CREATE INDEX idx_webhook_events_retention ON webhook_events (retention_until);
