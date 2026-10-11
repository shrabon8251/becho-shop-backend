-- V8: Subscriptions

CREATE TABLE IF NOT EXISTS subscription_plans (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id       BIGINT UNSIGNED NOT NULL,
  name           VARCHAR(128) NOT NULL,
  variant_id     BIGINT UNSIGNED NULL,
  `interval`     VARCHAR(32)  NOT NULL COMMENT 'weekly|monthly|quarterly|yearly',
  interval_count INT          NOT NULL DEFAULT 1,
  price_minor    BIGINT       NOT NULL,
  status         VARCHAR(32)  NOT NULL DEFAULT 'active',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_subplans_store (store_id, status),
  CONSTRAINT fk_subplans_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_subplans_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS subscriptions (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id        BIGINT UNSIGNED NOT NULL,
  plan_id         BIGINT UNSIGNED NOT NULL,
  customer_id     BIGINT UNSIGNED NOT NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'active',
  next_billing_at DATETIME(3)  NOT NULL,
  started_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  cancelled_at    DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_subs_due (store_id, status, next_billing_at) COMMENT 'scheduler picks due renewals',
  KEY idx_subs_customer (customer_id),
  CONSTRAINT fk_subs_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_subs_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_subs_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_subs_status CHECK (status IN ('active','paused','cancelled','expired'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
