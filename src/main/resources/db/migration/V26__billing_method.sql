-- V26: merchant billing payment method + subscription payment confirmation.
-- Fixes the dead-end subscribe() flow: subscribe() leaves billing_status as
-- 'pending_payment'; the merchant then pays (manual TrxID or SSLCommerz) and
-- confirms via POST /stores/{id}/billing/confirm, which flips it to 'active'.
--
-- TiDB rule: one schema change per ALTER TABLE.

ALTER TABLE stores ADD COLUMN billing_method_kind VARCHAR(16) NULL COMMENT 'bkash|nagad|rocket|sslcommerz';
ALTER TABLE stores ADD COLUMN billing_method_label VARCHAR(64) NULL;
ALTER TABLE stores ADD COLUMN billing_method_trxid VARCHAR(64) NULL COMMENT 'TrxID last used for a manual payment method';
ALTER TABLE stores ADD COLUMN billing_trx_id VARCHAR(64) NULL COMMENT 'TrxID of the subscription payment being confirmed';
