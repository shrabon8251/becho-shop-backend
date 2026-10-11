-- V45: Repair - re-create inventory tables skipped by baseline-on-migrate (V3 was baselined)

CREATE TABLE IF NOT EXISTS locations (
                                         id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                         store_id    BIGINT UNSIGNED NOT NULL,
                                         name        VARCHAR(128) NOT NULL,
    address     JSON         NULL,
    is_default  TINYINT(1)   NOT NULL DEFAULT 0,
    status      VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at  DATETIME(3)  NULL,
    PRIMARY KEY (id),
    KEY idx_locations_store (store_id, status),
    CONSTRAINT fk_locations_store FOREIGN KEY (store_id) REFERENCES stores (id)
                                                                   ON UPDATE CASCADE ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_levels (
                                                location_id   BIGINT UNSIGNED NOT NULL,
                                                variant_id    BIGINT UNSIGNED NOT NULL,
                                                on_hand       INT NOT NULL DEFAULT 0,
                                                reserved      INT NOT NULL DEFAULT 0 COMMENT 'committed to unfulfilled orders',
                                                reorder_point INT NOT NULL DEFAULT 0,
                                                available     INT GENERATED ALWAYS AS (on_hand - reserved) STORED,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (location_id, variant_id),
    KEY idx_levels_variant (variant_id),
    CONSTRAINT fk_levels_location FOREIGN KEY (location_id) REFERENCES locations (id)
                                                                    ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_levels_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
                                                                    ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_levels_nonneg CHECK (on_hand >= 0 AND reserved >= 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_transfers (
                                                   id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                   store_id         BIGINT UNSIGNED NOT NULL,
                                                   transfer_number  BIGINT UNSIGNED NOT NULL,
                                                   from_location_id BIGINT UNSIGNED NOT NULL,
                                                   to_location_id   BIGINT UNSIGNED NOT NULL,
                                                   status           VARCHAR(32) NOT NULL DEFAULT 'draft',
    notes            TEXT NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_transfers_number (store_id, transfer_number),
    KEY idx_transfers_store (store_id, status),
    CONSTRAINT fk_transfers_store FOREIGN KEY (store_id) REFERENCES stores (id)
                                                                       ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_transfers_from FOREIGN KEY (from_location_id) REFERENCES locations (id)
                                                                       ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_to FOREIGN KEY (to_location_id) REFERENCES locations (id)
                                                                       ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_transfers_status CHECK (status IN ('draft','in_transit','received','cancelled'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_transfer_items (
                                                        id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                        transfer_id BIGINT UNSIGNED NOT NULL,
                                                        variant_id  BIGINT UNSIGNED NOT NULL,
                                                        quantity    INT NOT NULL,
                                                        PRIMARY KEY (id),
    KEY idx_titems_transfer (transfer_id),
    CONSTRAINT fk_titems_transfer FOREIGN KEY (transfer_id) REFERENCES inventory_transfers (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_titems_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_titems_qty CHECK (quantity > 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Append-only audit of every stock change. Source of truth for investigations.
CREATE TABLE IF NOT EXISTS stock_movements (
                                               id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                               store_id    BIGINT UNSIGNED NOT NULL,
                                               variant_id  BIGINT UNSIGNED NOT NULL,
                                               location_id BIGINT UNSIGNED NOT NULL,
                                               delta       INT NOT NULL COMMENT 'signed change to on_hand',
                                               reason      VARCHAR(64) NOT NULL COMMENT 'order_reserve|order_release|fulfill|transfer|adjustment|return|cycle_count',
    ref_type    VARCHAR(32) NULL,
    ref_id      BIGINT UNSIGNED NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_movements_lookup (store_id, variant_id, created_at),
    KEY idx_movements_location (location_id, created_at),
    CONSTRAINT fk_movements_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
