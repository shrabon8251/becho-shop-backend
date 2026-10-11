-- V34: Trial lifecycle — persist trial_ends_at on stores.
-- TiDB: one schema change per ALTER TABLE.

ALTER TABLE stores ADD COLUMN trial_ends_at DATETIME(3) NULL
  COMMENT 'Trial expiry; NULL = no trial (paid/lifetime)';
