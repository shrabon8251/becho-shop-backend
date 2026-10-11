-- V35: Shopper login lockout — same 5-strike pattern as merchant login.
-- TiDB: one schema change per ALTER TABLE.

ALTER TABLE customers ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
