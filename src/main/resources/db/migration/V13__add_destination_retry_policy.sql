ALTER TABLE webhook_destinations
    ADD COLUMN retry_max_attempts INTEGER NOT NULL DEFAULT 8,
    ADD COLUMN retry_base_delay_ms BIGINT NOT NULL DEFAULT 1000,
    ADD COLUMN retry_max_delay_ms BIGINT NOT NULL DEFAULT 3600000;

ALTER TABLE webhook_destinations
    ADD CONSTRAINT ck_destination_retry_attempts CHECK (retry_max_attempts BETWEEN 1 AND 25),
    ADD CONSTRAINT ck_destination_retry_delays CHECK (
        retry_base_delay_ms BETWEEN 1 AND 86400000
        AND retry_max_delay_ms BETWEEN retry_base_delay_ms AND 86400000
    );
