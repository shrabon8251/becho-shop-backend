-- V32: Automation run queue (Bug #15).
-- Queued automation emails waiting for their delay to elapse.

CREATE TABLE IF NOT EXISTS automation_runs (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id        BIGINT UNSIGNED NOT NULL,
  automation_id   BIGINT UNSIGNED NOT NULL,
  `trigger`       VARCHAR(32)  NOT NULL,
  recipient_email VARCHAR(255) NOT NULL,
  recipient_name  VARCHAR(255) NULL,
  vars            JSON         NULL COMMENT 'Template variables for subject/body',
  scheduled_for   DATETIME(3)  NOT NULL COMMENT 'When the email becomes due (created + delayHours)',
  sent_at         DATETIME(3)  NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|sent|skipped|failed',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  INDEX idx_autorun_due (store_id, status, scheduled_for),
  CONSTRAINT fk_autorun_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT fk_autorun_automation FOREIGN KEY (automation_id) REFERENCES automations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
