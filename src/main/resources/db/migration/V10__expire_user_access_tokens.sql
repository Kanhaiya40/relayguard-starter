ALTER TABLE user_access_credentials ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
UPDATE user_access_credentials SET expires_at = created_at + INTERVAL '30 days';
ALTER TABLE user_access_credentials ALTER COLUMN expires_at SET NOT NULL;
CREATE INDEX idx_user_access_credentials_expiration
    ON user_access_credentials (key_prefix, revoked_at, expires_at);
