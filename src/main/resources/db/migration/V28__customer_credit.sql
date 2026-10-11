-- V28: Store credit balance on customers.
-- TiDB rule: one schema change per ALTER TABLE.

ALTER TABLE customers ADD COLUMN credit_minor BIGINT NOT NULL DEFAULT 0;
