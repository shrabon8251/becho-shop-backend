-- V13: Finance payouts to merchants

CREATE TABLE IF NOT EXISTS payouts (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id     BIGINT UNSIGNED NOT NULL,
  amount_minor BIGINT       NOT NULL,
  currency     CHAR(3)      NOT NULL,
  status       VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending|in_transit|paid|failed',
  gateway      VARCHAR(64)  NULL,
  gateway_ref  VARCHAR(128) NULL,
  period_start DATE         NOT NULL,
  period_end   DATE         NOT NULL,
  paid_at      DATETIME(3)  NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_payouts_store (store_id, status, created_at),
  CONSTRAINT fk_payouts_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_payouts_amount CHECK (amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
