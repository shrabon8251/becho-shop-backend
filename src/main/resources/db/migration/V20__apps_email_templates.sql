-- V20: store apps config + email templates (missing endpoints H6/H7).

CREATE TABLE IF NOT EXISTS store_apps_config (
  store_id    BIGINT UNSIGNED NOT NULL,
  config      TEXT         NOT NULL COMMENT 'Raw JSON: {bar:{on,text},reviews:{on},installed:[]}',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (store_id),
  CONSTRAINT fk_appscfg_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_templates (
  store_id    BIGINT UNSIGNED NOT NULL,
  `key`       VARCHAR(64)  NOT NULL,
  subject     VARCHAR(255) NOT NULL,
  body        TEXT         NOT NULL,
  enabled     TINYINT(1)   NOT NULL DEFAULT 1,
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (store_id, `key`),
  CONSTRAINT fk_emailtpl_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
