-- V36: widen narrow integer columns to match entity int fields.
-- Hibernate schema validation maps Java int to INTEGER; the tables used
-- SMALLINT/TINYINT, which fails validation with "wrong column type".
-- One schema change per ALTER TABLE (TiDB rule).

ALTER TABLE redirects
    MODIFY COLUMN status_code INT NOT NULL DEFAULT 301;

ALTER TABLE reviews
    MODIFY COLUMN stars INT NOT NULL;
