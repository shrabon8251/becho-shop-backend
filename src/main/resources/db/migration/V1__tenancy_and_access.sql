-- V1: Tenancy, plans, stores, users, staff, sequences
-- Conventions: InnoDB, utf8mb4, BIGINT UNSIGNED AUTO_INCREMENT PKs,
-- money in minor units (poisha/cents), timestamps DATETIME(3) UTC.

CREATE TABLE IF NOT EXISTS plans (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name          VARCHAR(64)  NOT NULL,
  price_minor   BIGINT       NOT NULL DEFAULT 0 COMMENT 'monthly price in minor units',
  `interval`    VARCHAR(16)  NOT NULL DEFAULT 'monthly',
  trial_days    INT          NOT NULL DEFAULT 14,
  limits        JSON         NULL COMMENT '{"stores":1,"products":1000,"staff":5,"storage_mb":512}',
  features      JSON         NULL,
  is_active     TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenants (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name          VARCHAR(128) NOT NULL,
  slug          VARCHAR(64)  NOT NULL,
  plan_id       BIGINT UNSIGNED NULL,
  status        VARCHAR(32)  NOT NULL DEFAULT 'active',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_tenants_slug (slug),
  KEY idx_tenants_plan (plan_id),
  CONSTRAINT fk_tenants_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_tenants_status CHECK (status IN ('active','suspended','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stores (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(128) NOT NULL,
  slug          VARCHAR(64)  NOT NULL,
  domain        VARCHAR(255) NULL COMMENT 'custom domain, verified out-of-band',
  subdomain     VARCHAR(64)  NULL COMMENT 'xxx.becho.shop',
  currency      CHAR(3)      NOT NULL DEFAULT 'BDT',
  timezone      VARCHAR(64)  NOT NULL DEFAULT 'Asia/Dhaka',
  status        VARCHAR(32)  NOT NULL DEFAULT 'active',
  settings      JSON         NULL COMMENT 'checkout, notification, SEO settings',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at    DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_stores_slug (tenant_id, slug),
  UNIQUE KEY uq_stores_domain (domain),
  UNIQUE KEY uq_stores_subdomain (subdomain),
  KEY idx_stores_tenant (tenant_id, status),
  CONSTRAINT fk_stores_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_stores_status CHECK (status IN ('active','paused','suspended'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Platform-level login accounts (merchants). Shopper accounts live in customers (store-scoped).
CREATE TABLE IF NOT EXISTS users (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email             VARCHAR(255) NOT NULL,
  password_hash     VARCHAR(255) NULL COMMENT 'Argon2id hash; NULL for OAuth-only accounts',
  full_name         VARCHAR(128) NOT NULL,
  phone             VARCHAR(32)  NULL,
  email_verified_at DATETIME(3)  NULL,
  phone_verified_at DATETIME(3)  NULL,
  status            VARCHAR(32)  NOT NULL DEFAULT 'active',
  last_login_at     DATETIME(3)  NULL,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  CONSTRAINT chk_users_status CHECK (status IN ('active','locked','suspended'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_staff (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id      BIGINT UNSIGNED NOT NULL,
  user_id       BIGINT UNSIGNED NOT NULL,
  role          VARCHAR(32)  NOT NULL DEFAULT 'staff' COMMENT 'owner|admin|staff|support',
  permissions   JSON         NULL COMMENT 'fine-grained overrides, enforced server-side',
  status        VARCHAR(32)  NOT NULL DEFAULT 'active',
  invited_by    BIGINT UNSIGNED NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_staff_store_user (store_id, user_id),
  KEY idx_staff_user (user_id),
  CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_staff_role CHECK (role IN ('owner','admin','staff','support')),
  CONSTRAINT chk_staff_status CHECK (status IN ('invited','active','suspended','removed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Atomic per-store sequences (order numbers, transfer numbers, ...).
-- Usage: SELECT next_val FROM store_sequences WHERE store_id=? AND seq_name='order' FOR UPDATE;
--        UPDATE store_sequences SET next_val = next_val + 1 WHERE ...;
CREATE TABLE IF NOT EXISTS store_sequences (
  store_id   BIGINT UNSIGNED NOT NULL,
  seq_name   VARCHAR(64)     NOT NULL,
  next_val   BIGINT UNSIGNED NOT NULL DEFAULT 1001,
  PRIMARY KEY (store_id, seq_name),
  CONSTRAINT fk_seq_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
