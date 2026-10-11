-- V11: Platform operations — API keys, webhooks (+delivery log), activity log

CREATE TABLE IF NOT EXISTS api_keys (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id     BIGINT UNSIGNED NOT NULL,
  name         VARCHAR(128) NOT NULL,
  key_prefix   VARCHAR(16)  NOT NULL COMMENT 'visible prefix for identification',
  key_hash     CHAR(64)     NOT NULL COMMENT 'SHA-256 of the secret; plaintext shown ONCE at creation',
  scopes       JSON         NULL COMMENT '["orders:read","products:write"]',
  last_used_at DATETIME(3)  NULL,
  expires_at   DATETIME(3)  NULL,
  revoked_at   DATETIME(3)  NULL,
  created_by   BIGINT UNSIGNED NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_apikeys_hash (key_hash),
  KEY idx_apikeys_store (store_id, revoked_at),
  CONSTRAINT fk_apikeys_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS webhooks (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  topic       VARCHAR(128)  NOT NULL COMMENT 'orders/create, products/update, ...',
  url         VARCHAR(1024) NOT NULL,
  secret_hash CHAR(64)      NOT NULL COMMENT 'SHA-256; used for HMAC signature',
  status      VARCHAR(32)   NOT NULL DEFAULT 'active',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_webhooks_topic_url (store_id, topic, url(191)),
  CONSTRAINT fk_webhooks_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS webhook_deliveries (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  webhook_id    BIGINT UNSIGNED NOT NULL,
  event_id      CHAR(36)     NOT NULL COMMENT 'idempotency key per event',
  payload       JSON         NOT NULL,
  status        VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending|delivered|failed',
  attempts      SMALLINT     NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_whdeliv_event (webhook_id, event_id),
  KEY idx_whdeliv_retry (status, next_retry_at),
  CONSTRAINT fk_whdeliv_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Append-only audit trail. Never updated or deleted by the app.
CREATE TABLE IF NOT EXISTS activity_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  actor_type  VARCHAR(32)  NOT NULL COMMENT 'staff|api_key|system',
  actor_id    BIGINT UNSIGNED NULL,
  action      VARCHAR(128) NOT NULL COMMENT 'order.refund, product.update, ...',
  entity_type VARCHAR(64)  NULL,
  entity_id   BIGINT UNSIGNED NULL,
  changes     JSON         NULL,
  ip_address  VARCHAR(45)  NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_alog_store (store_id, created_at),
  KEY idx_alog_entity (store_id, entity_type, entity_id),
  CONSTRAINT fk_alog_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
