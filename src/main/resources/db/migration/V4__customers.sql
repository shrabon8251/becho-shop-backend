-- V4: Customers (store-scoped shopper accounts) and addresses

CREATE TABLE IF NOT EXISTS customers (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id           BIGINT UNSIGNED NOT NULL,
  email              VARCHAR(255) NULL,
  phone              VARCHAR(32)  NULL,
  first_name         VARCHAR(64)  NULL,
  last_name          VARCHAR(64)  NULL,
  password_hash      VARCHAR(255) NULL COMMENT 'Argon2id; NULL for guest customers',
  email_verified_at  DATETIME(3)  NULL,
  phone_verified_at  DATETIME(3)  NULL,
  accepts_marketing  TINYINT(1)   NOT NULL DEFAULT 0,
  total_spent_minor  BIGINT       NOT NULL DEFAULT 0,
  orders_count       INT          NOT NULL DEFAULT 0,
  tags               JSON         NULL,
  note               TEXT         NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'active',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at         DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_customers_email (store_id, email),
  KEY idx_customers_phone (store_id, phone),
  KEY idx_customers_name (store_id, last_name, first_name),
  CONSTRAINT fk_customers_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_customers_status CHECK (status IN ('active','blocked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_addresses (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_id          BIGINT UNSIGNED NOT NULL,
  store_id             BIGINT UNSIGNED NOT NULL,
  label                VARCHAR(64)  NULL,
  first_name           VARCHAR(64)  NULL,
  last_name            VARCHAR(64)  NULL,
  company              VARCHAR(128) NULL,
  address1             VARCHAR(255) NOT NULL,
  address2             VARCHAR(255) NULL,
  city                 VARCHAR(128) NOT NULL,
  province             VARCHAR(128) NULL,
  country              CHAR(2)      NOT NULL DEFAULT 'BD',
  postal_code          VARCHAR(32)  NULL,
  phone                VARCHAR(32)  NULL,
  is_default_shipping  TINYINT(1)   NOT NULL DEFAULT 0,
  is_default_billing   TINYINT(1)   NOT NULL DEFAULT 0,
  created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_caddr_customer (customer_id),
  CONSTRAINT fk_caddr_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_caddr_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
