-- V10: Marketing, abandoned checkouts, blog, redirects

CREATE TABLE IF NOT EXISTS campaigns (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id     BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(128) NOT NULL,
  channel      VARCHAR(16)  NOT NULL COMMENT 'email|sms',
  status       VARCHAR(32)  NOT NULL DEFAULT 'draft',
  subject      VARCHAR(255) NULL,
  body         MEDIUMTEXT   NULL,
  audience     JSON         NULL COMMENT 'segment rules',
  scheduled_at DATETIME(3)  NULL,
  sent_at      DATETIME(3)  NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_campaigns_store (store_id, status),
  CONSTRAINT fk_campaigns_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_campaigns_channel CHECK (channel IN ('email','sms')),
  CONSTRAINT chk_campaigns_status CHECK (status IN ('draft','scheduled','sending','sent','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS abandoned_checkouts (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id       BIGINT UNSIGNED NOT NULL,
  customer_id    BIGINT UNSIGNED NULL,
  email          VARCHAR(255) NULL,
  cart           JSON         NOT NULL COMMENT 'lines snapshot',
  subtotal_minor BIGINT       NOT NULL DEFAULT 0,
  total_minor    BIGINT       NOT NULL DEFAULT 0,
  currency       CHAR(3)      NOT NULL,
  recovered_at   DATETIME(3)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_abandoned_store (store_id, recovered_at, created_at),
  CONSTRAINT fk_abandoned_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_posts (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id     BIGINT UNSIGNED NOT NULL,
  title        VARCHAR(255) NOT NULL,
  slug         VARCHAR(255) NOT NULL,
  body         MEDIUMTEXT   NULL,
  status       VARCHAR(32)  NOT NULL DEFAULT 'draft',
  published_at DATETIME(3)  NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at   DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_blogposts_slug (store_id, slug),
  KEY idx_blogposts_store (store_id, status, published_at),
  CONSTRAINT fk_blogposts_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS redirects (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  from_path   VARCHAR(1024) NOT NULL,
  to_path     VARCHAR(1024) NOT NULL,
  status_code SMALLINT      NOT NULL DEFAULT 301,
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_redirects_from (store_id, from_path(191)),
  CONSTRAINT fk_redirects_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_redirects_code CHECK (status_code IN (301,302))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
