-- V7: Gift cards, loyalty points, store credit (all with transaction ledgers)

CREATE TABLE IF NOT EXISTS gift_cards (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id         BIGINT UNSIGNED NOT NULL,
  code_hash        CHAR(64)     NOT NULL COMMENT 'SHA-256 of the code; code shown once at issue',
  code_prefix      VARCHAR(8)   NOT NULL COMMENT 'first chars for support lookup',
  initial_minor    BIGINT       NOT NULL,
  balance_minor    BIGINT       NOT NULL,
  currency         CHAR(3)      NOT NULL,
  status           VARCHAR(32)  NOT NULL DEFAULT 'active',
  expires_at       DATETIME(3)  NULL,
  issued_to_email  VARCHAR(255) NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_giftcards_code (code_hash),
  KEY idx_giftcards_store (store_id, status),
  CONSTRAINT fk_giftcards_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_giftcards_money CHECK (initial_minor > 0 AND balance_minor >= 0),
  CONSTRAINT chk_giftcards_status CHECK (status IN ('active','used_up','expired','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gift_card_transactions (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  gift_card_id      BIGINT UNSIGNED NOT NULL,
  order_id          BIGINT UNSIGNED NULL,
  amount_minor      BIGINT       NOT NULL COMMENT 'negative = spent',
  balance_after_minor BIGINT     NOT NULL,
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_gctxn_card (gift_card_id, created_at),
  CONSTRAINT fk_gctxn_card FOREIGN KEY (gift_card_id) REFERENCES gift_cards (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS loyalty_accounts (
  customer_id BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  points      BIGINT      NOT NULL DEFAULT 0,
  tier        VARCHAR(32) NOT NULL DEFAULT 'bronze',
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (customer_id),
  KEY idx_loyalty_store (store_id, tier),
  CONSTRAINT fk_loyalty_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_loyalty_tier CHECK (tier IN ('bronze','silver','gold','platinum'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS loyalty_transactions (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_id BIGINT UNSIGNED NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  points_delta BIGINT       NOT NULL COMMENT 'signed',
  reason      VARCHAR(128) NOT NULL,
  ref_type    VARCHAR(32)  NULL,
  ref_id      BIGINT UNSIGNED NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_loyaltxn_customer (customer_id, created_at),
  CONSTRAINT fk_loyaltxn_customer FOREIGN KEY (customer_id) REFERENCES loyalty_accounts (customer_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_credits (
  customer_id   BIGINT UNSIGNED NOT NULL,
  store_id      BIGINT UNSIGNED NOT NULL,
  balance_minor BIGINT      NOT NULL DEFAULT 0,
  currency      CHAR(3)     NOT NULL,
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (customer_id),
  CONSTRAINT fk_scredit_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_scredit_balance CHECK (balance_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_credit_transactions (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_id        BIGINT UNSIGNED NOT NULL,
  store_id           BIGINT UNSIGNED NOT NULL,
  amount_minor       BIGINT       NOT NULL COMMENT 'signed',
  balance_after_minor BIGINT      NOT NULL,
  reason             VARCHAR(128) NOT NULL,
  ref_type           VARCHAR(32)  NULL,
  ref_id             BIGINT UNSIGNED NULL,
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_sctxn_customer (customer_id, created_at),
  CONSTRAINT fk_sctxn_customer FOREIGN KEY (customer_id) REFERENCES store_credits (customer_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
