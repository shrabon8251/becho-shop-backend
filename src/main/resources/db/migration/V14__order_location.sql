-- V14: track the fulfilling location on orders so cancellation can release reservations.
-- NOTE: TiDB validates a FOREIGN KEY against the pre-ALTER schema, so ADD COLUMN and
-- ADD CONSTRAINT (FK) on the new column cannot share one ALTER TABLE (Error 1072).
-- Keep them as separate statements for TiDB compatibility (works on MySQL too).
ALTER TABLE orders
  ADD COLUMN location_id BIGINT UNSIGNED NULL COMMENT 'location stock was reserved from' AFTER customer_id;
ALTER TABLE orders
  ADD CONSTRAINT fk_orders_location FOREIGN KEY (location_id) REFERENCES locations (id);
CREATE INDEX idx_orders_location ON orders (store_id, location_id);
