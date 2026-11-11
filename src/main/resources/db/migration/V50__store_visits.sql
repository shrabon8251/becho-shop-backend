-- V50: Storefront visitor tracking for the dashboard Conversion Rate card.
-- One row per (store, day, visitor hash). No IP / User-Agent is stored —
-- the hash is computed in the app from (IP + User-Agent + day).
-- TiDB: one statement per migration file section is fine; CREATE TABLE is a
-- single statement.
CREATE TABLE IF NOT EXISTS store_visits (
                                            id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                            created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    store_id           BIGINT UNSIGNED NOT NULL,
    visited_date       DATE NOT NULL,
    visitor_hash       VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_store_visits_day_visitor (store_id, visited_date, visitor_hash),
    KEY idx_store_visits_store_date (store_id, visited_date),
    CONSTRAINT fk_store_visits_store FOREIGN KEY (store_id) REFERENCES stores (id)
    );
