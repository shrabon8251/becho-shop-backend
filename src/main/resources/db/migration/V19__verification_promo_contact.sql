-- V19: verification codes, terms acceptance, contact messages, promo codes.
-- All secrets stay in env vars; only hashes are stored here.

-- Email verification codes (6-digit, hash-only storage)
CREATE TABLE IF NOT EXISTS email_verification_codes (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  code_hash   VARCHAR(64)  NOT NULL COMMENT 'SHA-256 of the 6-digit code; never plaintext',
  expires_at  DATETIME(3)  NOT NULL,
  attempts    INT          NOT NULL DEFAULT 0,
  used_at     DATETIME(3)  NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_evc_user (user_id, used_at, created_at),
  CONSTRAINT fk_evc_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Phone verification (OTP) codes
CREATE TABLE IF NOT EXISTS phone_verification_codes (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  phone       VARCHAR(32)  NOT NULL,
  code_hash   VARCHAR(64)  NOT NULL COMMENT 'SHA-256 of the 6-digit OTP; never plaintext',
  expires_at  DATETIME(3)  NOT NULL,
  attempts    INT          NOT NULL DEFAULT 0,
  used_at     DATETIME(3)  NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_pvc_user (user_id, used_at, created_at),
  CONSTRAINT fk_pvc_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Terms & Privacy acceptance timestamp on signup
ALTER TABLE users
  ADD COLUMN terms_accepted_at DATETIME(3) NULL
    COMMENT 'When the user accepted Terms & Privacy during signup';

-- Contact form messages
CREATE TABLE IF NOT EXISTS contact_messages (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(128) NOT NULL,
  email       VARCHAR(255) NOT NULL,
  subject     VARCHAR(255) NULL,
  message     TEXT         NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_contact_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Platform promo codes (e.g. BECHO10): server-side validated, atomic redemption
CREATE TABLE IF NOT EXISTS promo_codes (
  code         VARCHAR(32)  NOT NULL,
  percent_off  DECIMAL(5,2) NOT NULL COMMENT 'e.g. 10.00 = 10% off',
  max_uses     INT          NOT NULL DEFAULT 1000000,
  used_count   INT          NOT NULL DEFAULT 0,
  is_active    TINYINT(1)   NOT NULL DEFAULT 1,
  expires_at   DATETIME(3)  NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (code),
  CONSTRAINT chk_promo_percent CHECK (percent_off >= 0 AND percent_off <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One redemption per store per code (UNIQUE = atomic, no double-use)
CREATE TABLE IF NOT EXISTS promo_redemptions (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32) NOT NULL,
  store_id    BIGINT UNSIGNED NOT NULL,
  redeemed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_promo_store (code, store_id),
  KEY idx_redemption_store (store_id),
  CONSTRAINT fk_redemption_code FOREIGN KEY (code) REFERENCES promo_codes (code),
  CONSTRAINT fk_redemption_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed the launch promo: BECHO10 = 10% off, active
INSERT INTO promo_codes (code, percent_off, max_uses, is_active)
VALUES ('BECHO10', 10.00, 1000000, 1)
ON DUPLICATE KEY UPDATE percent_off = VALUES(percent_off), is_active = VALUES(is_active);

-- Store's chosen platform plan (set by /billing/subscribe)
ALTER TABLE stores
  ADD COLUMN plan_name VARCHAR(32) NULL COMMENT 'Starter|Growth|Pro',
  ADD COLUMN billing_cycle VARCHAR(16) NULL COMMENT 'monthly|quarterly|halfyearly|yearly';
