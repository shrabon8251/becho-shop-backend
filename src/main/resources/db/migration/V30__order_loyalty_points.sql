-- V30: Track loyalty points on orders (Bug #11).
-- Points must be deducted atomically with order creation and
-- refunded/reversed on cancel. TiDB: one action per ALTER.

ALTER TABLE orders ADD COLUMN loyalty_points_used INT NOT NULL DEFAULT 0;
