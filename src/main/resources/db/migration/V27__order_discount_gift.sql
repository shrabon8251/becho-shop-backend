-- V27: Track discount/gift usage on orders for cancellation reversal.
-- TiDB rule: one schema change per ALTER TABLE.

ALTER TABLE orders ADD COLUMN discount_code VARCHAR(64) NULL;
ALTER TABLE orders ADD COLUMN gift_code_hash VARCHAR(64) NULL;
ALTER TABLE orders ADD COLUMN gift_applied_minor BIGINT NOT NULL DEFAULT 0;
