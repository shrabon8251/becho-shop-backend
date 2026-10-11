-- V35: add columns required by Hibernate schema validation.
-- V22 omitted updated_at on domains and reviews; V13 payouts and V10
-- redirects also lack it; V25 platform_landing lacks created_at.
-- Their entities extend BaseEntity, which requires these columns.
-- One schema change per ALTER TABLE (TiDB rule).

ALTER TABLE domains
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

ALTER TABLE reviews
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

ALTER TABLE payouts
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

ALTER TABLE redirects
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

ALTER TABLE platform_landing
    ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
