-- V15: persisted refresh tokens with rotation (theft detection via reuse).
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  token_hash    CHAR(64)     NOT NULL COMMENT 'SHA-256 of the JWT; the token itself is never stored',
  expires_at    DATETIME(3)  NOT NULL,
  revoked_at    DATETIME(3)  NULL,
  replaced_by   BIGINT UNSIGNED NULL COMMENT 'id of the rotated successor; reuse => theft',
  ip            VARCHAR(45)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_token_hash (token_hash),
  KEY idx_refresh_user (user_id, revoked_at),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
