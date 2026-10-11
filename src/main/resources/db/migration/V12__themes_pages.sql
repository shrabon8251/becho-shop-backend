-- V12: Themes and storefront pages

CREATE TABLE IF NOT EXISTS themes (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id     BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(128) NOT NULL,
  version      VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
  settings     JSON         NULL COMMENT 'theme builder config (colors, sections, layout)',
  is_published TINYINT(1)   NOT NULL DEFAULT 0,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_themes_store (store_id, is_published),
  CONSTRAINT fk_themes_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pages (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id   BIGINT UNSIGNED NOT NULL,
  title      VARCHAR(255) NOT NULL,
  slug       VARCHAR(255) NOT NULL,
  body       MEDIUMTEXT   NULL,
  status     VARCHAR(32)  NOT NULL DEFAULT 'draft',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_pages_slug (store_id, slug),
  CONSTRAINT fk_pages_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
