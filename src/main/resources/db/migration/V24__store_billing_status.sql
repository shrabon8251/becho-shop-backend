-- V24: Store billing status (pending_payment vs active)
-- Fixes: subscribe() must not activate a plan without payment.

ALTER TABLE stores ADD COLUMN billing_status VARCHAR(24) NOT NULL DEFAULT 'active'
  COMMENT 'active|pending_payment|past_due|cancelled';
