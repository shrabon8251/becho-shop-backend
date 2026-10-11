-- V18: api_keys.status (entity expects it).
ALTER TABLE api_keys
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT 'active|revoked' AFTER scopes;
