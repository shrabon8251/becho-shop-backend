-- V25: Platform landing page CMS (super admin Website tab)
-- Single-row table (id=1) holding the LandingContent JSON.

CREATE TABLE IF NOT EXISTS platform_landing (
  id          BIGINT UNSIGNED NOT NULL,
  content     JSON         NOT NULL COMMENT 'LandingContent JSON',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
