-- V23: Password reset codes.
-- 6-digit codes, hash-only, 15-min expiry, single-use.
-- user_id nullable: we rate-limit by email even when no user exists
-- (enumeration protection requires identical behavior for unknown emails).
-- NOTE: promo_codes, promo_redemptions and contact_messages are owned by V19
-- (their shapes must match the JPA entities: PromoCode has PK(code),
-- PromoRedemption joins on code). They are intentionally NOT redefined here.

CREATE TABLE IF NOT EXISTS password_reset_codes (
                                                    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                    user_id     BIGINT UNSIGNED NULL,
                                                    email       VARCHAR(255) NOT NULL COMMENT 'for rate-limiting even when user unknown',
    code_hash   VARCHAR(64)  NOT NULL COMMENT 'SHA-256 of the 6-digit code; never plaintext',
    expires_at  DATETIME(3)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    used_at     DATETIME(3)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_prc_user (user_id, used_at, created_at),
    KEY idx_prc_email (email, created_at),
    CONSTRAINT fk_prc_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
