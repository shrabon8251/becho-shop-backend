-- V6: Orders, lines, payments, fulfillments, refunds
-- Money integrity: totals are computed in the app inside ONE DB transaction
-- together with inventory reservation. Never trust client-sent totals.

CREATE TABLE IF NOT EXISTS orders (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id           BIGINT UNSIGNED NOT NULL,
  order_number       BIGINT UNSIGNED NOT NULL COMMENT 'per-store sequence from store_sequences',
  customer_id        BIGINT UNSIGNED NULL,
  email              VARCHAR(255) NULL,
  phone              VARCHAR(32)  NULL,
  financial_status   VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending|paid|partially_paid|refunded|voided',
  fulfillment_status VARCHAR(32) NOT NULL DEFAULT 'unfulfilled' COMMENT 'unfulfilled|partial|fulfilled|returned',
  subtotal_minor     BIGINT      NOT NULL,
  discount_minor     BIGINT      NOT NULL DEFAULT 0,
  shipping_minor     BIGINT      NOT NULL DEFAULT 0,
  tax_minor          BIGINT      NOT NULL DEFAULT 0,
  total_minor        BIGINT      NOT NULL,
  currency           CHAR(3)     NOT NULL,
  payment_method     VARCHAR(64) NULL COMMENT 'cod|bkash|nagad|sslcommerz|stripe|...',
  source             VARCHAR(32) NOT NULL DEFAULT 'web' COMMENT 'web|pos|draft|subscription',
  notes              TEXT        NULL,
  billing_address    JSON        NULL COMMENT 'snapshot at order time',
  shipping_address   JSON        NULL COMMENT 'snapshot at order time',
  tags               JSON        NULL,
  fraud_score        INT         NULL,
  processed_at       DATETIME(3) NULL,
  cancelled_at       DATETIME(3) NULL,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at         DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_orders_number (store_id, order_number),
  KEY idx_orders_customer (store_id, customer_id, created_at),
  KEY idx_orders_financial (store_id, financial_status, created_at),
  KEY idx_orders_fulfillment (store_id, fulfillment_status, created_at),
  KEY idx_orders_created (store_id, created_at),
  CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_orders_money CHECK (subtotal_minor >= 0 AND total_minor >= 0
    AND discount_minor >= 0 AND shipping_minor >= 0 AND tax_minor >= 0),
  CONSTRAINT chk_orders_fin CHECK (financial_status IN ('pending','paid','partially_paid','partially_refunded','refunded','voided')),
  CONSTRAINT chk_orders_ful CHECK (fulfillment_status IN ('unfulfilled','partial','fulfilled','returned'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_lines (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id       BIGINT UNSIGNED NOT NULL,
  store_id       BIGINT UNSIGNED NOT NULL,
  product_id     BIGINT UNSIGNED NULL,
  variant_id     BIGINT UNSIGNED NULL,
  title          VARCHAR(255) NOT NULL COMMENT 'snapshot',
  sku            VARCHAR(64)  NULL,
  quantity       INT          NOT NULL,
  price_minor    BIGINT       NOT NULL COMMENT 'unit price snapshot',
  discount_minor BIGINT       NOT NULL DEFAULT 0,
  tax_minor      BIGINT       NOT NULL DEFAULT 0,
  total_minor    BIGINT       NOT NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_olines_order (order_id),
  KEY idx_olines_variant (store_id, variant_id),
  CONSTRAINT fk_olines_order FOREIGN KEY (order_id) REFERENCES orders (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_olines_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_olines_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Every money movement on an order. Reconciles with gateway webhooks (IPN).
CREATE TABLE IF NOT EXISTS order_transactions (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id       BIGINT UNSIGNED NOT NULL,
  store_id       BIGINT UNSIGNED NOT NULL,
  kind           VARCHAR(32)  NOT NULL COMMENT 'sale|authorization|capture|refund|void',
  gateway        VARCHAR(64)  NOT NULL,
  amount_minor   BIGINT       NOT NULL,
  currency       CHAR(3)      NOT NULL,
  status         VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending|success|failed',
  gateway_txn_id VARCHAR(128) NULL,
  failure_reason VARCHAR(255) NULL,
  raw            JSON         NULL COMMENT 'gateway payload (no secrets)',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_otxn_order (order_id, created_at),
  UNIQUE KEY uq_otxn_gateway (store_id, gateway, gateway_txn_id),
  CONSTRAINT fk_otxn_order FOREIGN KEY (order_id) REFERENCES orders (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_otxn_kind CHECK (kind IN ('sale','authorization','capture','refund','void')),
  CONSTRAINT chk_otxn_status CHECK (status IN ('pending','success','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fulfillments (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id        BIGINT UNSIGNED NOT NULL,
  store_id        BIGINT UNSIGNED NOT NULL,
  location_id     BIGINT UNSIGNED NULL,
  tracking_number VARCHAR(128) NULL,
  courier         VARCHAR(64)  NULL COMMENT 'pathao|redx|steadfast|paperfly|ecourier',
  status          VARCHAR(32)  NOT NULL DEFAULT 'pending',
  shipped_at      DATETIME(3)  NULL,
  delivered_at    DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_fulfill_order (order_id),
  KEY idx_fulfill_tracking (store_id, tracking_number),
  CONSTRAINT fk_fulfill_order FOREIGN KEY (order_id) REFERENCES orders (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_fulfill_location FOREIGN KEY (location_id) REFERENCES locations (id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_fulfill_status CHECK (status IN ('pending','shipped','in_transit','delivered','cancelled','returned'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fulfillment_lines (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  fulfillment_id BIGINT UNSIGNED NOT NULL,
  order_line_id  BIGINT UNSIGNED NOT NULL,
  quantity       INT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_flines_fulfillment (fulfillment_id),
  CONSTRAINT fk_flines_fulfillment FOREIGN KEY (fulfillment_id) REFERENCES fulfillments (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_flines_oline FOREIGN KEY (order_line_id) REFERENCES order_lines (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_flines_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refunds (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id     BIGINT UNSIGNED NOT NULL,
  store_id     BIGINT UNSIGNED NOT NULL,
  amount_minor BIGINT       NOT NULL,
  reason       VARCHAR(255) NULL,
  status       VARCHAR(32)  NOT NULL DEFAULT 'pending',
  processed_by BIGINT UNSIGNED NULL COMMENT 'store_staff.id',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_refunds_order (order_id),
  CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_refunds_amount CHECK (amount_minor > 0),
  CONSTRAINT chk_refunds_status CHECK (status IN ('pending','succeeded','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refund_lines (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  refund_id     BIGINT UNSIGNED NOT NULL,
  order_line_id BIGINT UNSIGNED NOT NULL,
  quantity      INT    NOT NULL,
  amount_minor  BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rflines_refund (refund_id),
  CONSTRAINT fk_rflines_refund FOREIGN KEY (refund_id) REFERENCES refunds (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_rflines_oline FOREIGN KEY (order_line_id) REFERENCES order_lines (id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- discount_usages.order_id FK added here (orders table did not exist in V5)
ALTER TABLE discount_usages
  ADD CONSTRAINT fk_dusage_order FOREIGN KEY (order_id) REFERENCES orders (id)
    ON UPDATE CASCADE ON DELETE CASCADE;
