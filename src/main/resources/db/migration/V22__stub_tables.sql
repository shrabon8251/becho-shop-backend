-- V22: tables for DB-backed controllers (replacing in-memory stubs).

CREATE TABLE IF NOT EXISTS automations (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  name        VARCHAR(128) NOT NULL,
  `trigger`   VARCHAR(32)  NOT NULL COMMENT 'abandoned_checkout|welcome|winback',
  active      TINYINT(1)   NOT NULL DEFAULT 1,
  delay_hours INT          NOT NULL DEFAULT 24,
  subject     VARCHAR(255) NOT NULL,
  body        MEDIUMTEXT   NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_automations_store (store_id, active),
  CONSTRAINT fk_automations_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bundles (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  name        VARCHAR(128) NOT NULL,
  items       JSON         NOT NULL COMMENT 'bundle items: [{productId,variantId,qty}]',
  price_minor BIGINT       NOT NULL,
  image       VARCHAR(1024) NULL,
  active      TINYINT(1)   NOT NULL DEFAULT 1,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_bundles_store (store_id, active),
  CONSTRAINT fk_bundles_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS domains (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id   BIGINT UNSIGNED NOT NULL,
  domain     VARCHAR(255) NOT NULL,
  `primary`  TINYINT(1)   NOT NULL DEFAULT 0,
  status     VARCHAR(32)  NOT NULL DEFAULT 'Pending' COMMENT 'Pending|Connected|Error',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_domains_domain (domain),
  KEY idx_domains_store (store_id),
  CONSTRAINT fk_domains_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS draft_orders (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NULL,
  email       VARCHAR(255) NULL,
  `lines`     JSON         NOT NULL COMMENT 'draft lines snapshot',
  subtotal_minor BIGINT    NOT NULL DEFAULT 0,
  total_minor BIGINT       NOT NULL DEFAULT 0,
  currency    CHAR(3)      NOT NULL,
  status      VARCHAR(32)  NOT NULL DEFAULT 'draft',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_draft_orders_store (store_id, status),
  CONSTRAINT fk_draft_orders_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS returns (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  order_id    BIGINT UNSIGNED NOT NULL,
  reason      VARCHAR(255) NOT NULL,
  status      VARCHAR(32)  NOT NULL DEFAULT 'requested' COMMENT 'requested|approved|rejected|completed',
  items       JSON         NULL COMMENT 'returned items',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_returns_store (store_id, status),
  KEY idx_returns_order (order_id),
  CONSTRAINT fk_returns_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_returns_order FOREIGN KEY (order_id) REFERENCES orders (id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_segments (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id       BIGINT UNSIGNED NOT NULL,
  name           VARCHAR(128) NOT NULL,
  rules          JSON         NULL COMMENT 'segment rules',
  customer_count INT          NOT NULL DEFAULT 0,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_segments_store (store_id),
  CONSTRAINT fk_segments_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shipping_profiles (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id   BIGINT UNSIGNED NOT NULL,
  name       VARCHAR(128) NOT NULL,
  zones      JSON         NULL COMMENT 'shipping zones',
  active     TINYINT(1)   NOT NULL DEFAULT 1,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ship_profiles_store (store_id, active),
  CONSTRAINT fk_ship_profiles_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id   BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  name       VARCHAR(128) NOT NULL,
  stars      TINYINT      NOT NULL,
  text       MEDIUMTEXT   NOT NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_reviews_store_product (store_id, product_id, created_at),
  CONSTRAINT fk_reviews_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_reviews_stars CHECK (stars >= 1 AND stars <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS support_tickets (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id       BIGINT UNSIGNED NOT NULL,
  subject        VARCHAR(255) NOT NULL,
  message        MEDIUMTEXT   NOT NULL,
  status         VARCHAR(32)  NOT NULL DEFAULT 'open' COMMENT 'open|pending|resolved|closed',
  customer_email VARCHAR(255) NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tickets_store (store_id, status, created_at),
  CONSTRAINT fk_tickets_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_alerts (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id   BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  email      VARCHAR(255) NOT NULL,
  notified   TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_stock_alerts (store_id, product_id, email),
  KEY idx_stock_alerts_pending (store_id, product_id, notified),
  CONSTRAINT fk_stock_alerts_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_stock_alerts_product FOREIGN KEY (product_id) REFERENCES products (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
