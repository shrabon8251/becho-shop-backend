-- V5: Discounts and usage tracking

CREATE TABLE IF NOT EXISTS discounts (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id              BIGINT UNSIGNED NOT NULL,
  code                  VARCHAR(64)  NOT NULL,
  title                 VARCHAR(255) NOT NULL,
  discount_type         VARCHAR(32)  NOT NULL COMMENT 'percentage|fixed_amount|buy_x_get_y|free_shipping',
  value_percent         DECIMAL(5,2) NULL COMMENT 'for percentage type',
  value_minor           BIGINT       NULL COMMENT 'for fixed_amount type',
  buy_x                 INT          NULL,
  get_y                 INT          NULL,
  applies_to            VARCHAR(32)  NOT NULL DEFAULT 'order' COMMENT 'order|products|collections',
  eligibility           JSON         NULL COMMENT 'product/collection/customer constraints',
  minimum_subtotal_minor BIGINT      NULL,
  usage_limit           INT          NULL,
  used_count            INT          NOT NULL DEFAULT 0,
  starts_at             DATETIME(3)  NOT NULL,
  ends_at               DATETIME(3)  NULL,
  status                VARCHAR(32)  NOT NULL DEFAULT 'active',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at            DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_discounts_code (store_id, code),
  KEY idx_discounts_active (store_id, status, starts_at, ends_at),
  CONSTRAINT fk_discounts_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_discounts_type CHECK (discount_type IN ('percentage','fixed_amount','buy_x_get_y','free_shipping')),
  CONSTRAINT chk_discounts_status CHECK (status IN ('active','expired','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS discount_usages (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  discount_id BIGINT UNSIGNED NOT NULL,
  order_id    BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NULL,
  used_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_dusage_discount (discount_id, used_at),
  KEY idx_dusage_order (order_id),
  CONSTRAINT fk_dusage_discount FOREIGN KEY (discount_id) REFERENCES discounts (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
