-- Add video_url to products for product video support.
ALTER TABLE products ADD COLUMN video_url VARCHAR(512) NULL;
