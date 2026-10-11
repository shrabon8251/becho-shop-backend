-- V9: Shipping zones/rates and tax rates

CREATE TABLE IF NOT EXISTS shipping_zones (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id   BIGINT UNSIGNED NOT NULL,
  name       VARCHAR(128) NOT NULL,
  countries  JSON         NOT NULL COMMENT '["BD","IN"] — ISO codes',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_shipzones_store (store_id),
  CONSTRAINT fk_shipzones_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shipping_rates (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  zone_id              BIGINT UNSIGNED NOT NULL,
  store_id             BIGINT UNSIGNED NOT NULL,
  name                 VARCHAR(128) NOT NULL,
  price_minor          BIGINT       NOT NULL,
  min_subtotal_minor   BIGINT       NULL COMMENT 'free-shipping threshold style rules',
  max_weight_grams     INT          NULL,
  delivery_eta         VARCHAR(64)  NULL,
  status               VARCHAR(32)  NOT NULL DEFAULT 'active',
  position             INT          NOT NULL DEFAULT 0,
  created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_shiprates_zone (zone_id, position),
  CONSTRAINT fk_shiprates_zone FOREIGN KEY (zone_id) REFERENCES shipping_zones (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_shiprates_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_shiprates_price CHECK (price_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tax_rates (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id     BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(128) NOT NULL,
  country      CHAR(2)      NOT NULL,
  region       VARCHAR(128) NULL,
  rate_percent DECIMAL(5,2) NOT NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_taxrates_store (store_id, country),
  CONSTRAINT fk_taxrates_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_taxrates_rate CHECK (rate_percent >= 0 AND rate_percent <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
