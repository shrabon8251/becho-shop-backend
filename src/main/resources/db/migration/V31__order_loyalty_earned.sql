-- V31: loyalty_points_earned column.

ALTER TABLE orders ADD COLUMN loyalty_points_earned INT NOT NULL DEFAULT 0;
