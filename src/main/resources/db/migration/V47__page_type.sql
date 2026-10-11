-- Add type column to pages (post|page) for blog vs nav distinction
ALTER TABLE pages ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'page';
