-- V16: align schema with the entity model (columns the domain needs that V1-V15 omitted).
-- TiDB quirk: use ONE ADD COLUMN per ALTER TABLE statement, and AFTER must
-- reference a column that already exists (not one added in the same statement).

ALTER TABLE discounts
  ADD COLUMN conditions JSON NULL COMMENT 'extra rule JSON (buy_x_get_y config, etc.)' AFTER eligibility;

ALTER TABLE discounts
  ADD COLUMN per_customer_limit INT NULL COMMENT 'max uses per customer' AFTER used_count;

ALTER TABLE shipping_rates
  ADD COLUMN rate_type VARCHAR(32) NOT NULL DEFAULT 'flat' COMMENT 'flat|free|conditional' AFTER name;

ALTER TABLE shipping_rates
  ADD COLUMN conditions JSON NULL COMMENT 'price/weight condition JSON' AFTER rate_type;

ALTER TABLE shipping_rates
  ADD COLUMN min_days INT NULL AFTER conditions;

ALTER TABLE shipping_rates
  ADD COLUMN max_days INT NULL AFTER min_days;

ALTER TABLE webhooks
  ADD COLUMN headers JSON NULL COMMENT 'extra headers to send' AFTER secret_hash;

ALTER TABLE webhooks
  ADD COLUMN failure_count INT NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE loyalty_accounts
  ADD COLUMN lifetime_points INT NOT NULL DEFAULT 0 COMMENT 'all-time earned; drives tiers' AFTER points;

ALTER TABLE themes
  ADD COLUMN preset_key VARCHAR(64) NULL COMMENT 'minimal|fashion|bold|craft|...' AFTER version;

ALTER TABLE pages
  ADD COLUMN sections JSON NULL COMMENT 'theme-builder section JSON' AFTER body;

-- Customer product subscriptions (recurring purchases). The `subscriptions`
-- table from V8 is the store's SaaS plan billing; this is the shopper-facing one.
CREATE TABLE IF NOT EXISTS customer_subscriptions (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id        BIGINT UNSIGNED NOT NULL,
  customer_id     BIGINT UNSIGNED NOT NULL,
  plan_name       VARCHAR(128) NOT NULL,
  `interval`      VARCHAR(16)  NOT NULL DEFAULT 'monthly' COMMENT 'daily|weekly|monthly|yearly',
  price_minor     BIGINT       NOT NULL,
  currency        CHAR(3)      NOT NULL DEFAULT 'BDT',
  status          VARCHAR(32)  NOT NULL DEFAULT 'active',
  next_billing_at DATETIME(3)  NULL,
  cancelled_at    DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_csubs_customer (store_id, customer_id, status),
  KEY idx_csubs_renewal (status, next_billing_at),
  CONSTRAINT fk_csubs_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_csubs_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
