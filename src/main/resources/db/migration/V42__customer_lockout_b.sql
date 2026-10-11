-- V36: Shopper login lockout (part 2).
-- TiDB: one schema change per ALTER TABLE.

ALTER TABLE customers ADD COLUMN locked_until DATETIME(3) NULL;
