-- V33: Demo seed data for store id=1 (frontend demo mode).
-- Idempotent: only seeds if store id=1 does not exist.
-- Products use Unsplash images, prices in BDT (minor units).

-- Create tenant and store id=1 if not exists.
INSERT IGNORE INTO tenants (id, name, slug, created_at, updated_at)
VALUES (1, 'Demo Tenant', 'demo-tenant', NOW(3), NOW(3));

INSERT IGNORE INTO stores (id, tenant_id, name, slug, subdomain, currency, timezone, status, settings, created_at, updated_at)
VALUES (1, 1, 'Demo Store', 'demo-store', 'demo', 'BDT', 'Asia/Dhaka', 'active',
  '{"storeName":"Demo Store","currency":"BDT","timezone":"Asia/Dhaka"}',
  NOW(3), NOW(3));

-- 12 demo products (only if store 1 has no products yet).
-- We use a guard: insert only if products table has no rows for store 1.

-- Helper: this migration is idempotent via INSERT IGNORE on unique slugs.

INSERT IGNORE INTO products (store_id, title, slug, description, status, product_type, vendor, published_at)
VALUES
(1, 'Classic Sneakers', 'classic-sneakers', 'Comfortable everyday sneakers with premium cushioning.', 'active', 'Footwear', 'Becho', NOW(3)),
(1, 'Leather Wallet', 'leather-wallet', 'Genuine leather bifold wallet with RFID protection.', 'active', 'Accessories', 'Becho', NOW(3)),
(1, 'Everyday Tote', 'everyday-tote', 'Spacious canvas tote for daily essentials.', 'active', 'Bags', 'Becho', NOW(3)),
(1, 'Denim Jacket', 'denim-jacket', 'Classic denim jacket with a modern fit.', 'active', 'Apparel', 'Becho', NOW(3)),
(1, 'Ceramic Mug', 'ceramic-mug', 'Handcrafted ceramic mug, 350ml.', 'active', 'Home', 'Becho', NOW(3)),
(1, 'Cotton Tee', 'cotton-tee', '100% organic cotton t-shirt.', 'active', 'Apparel', 'Becho', NOW(3)),
(1, 'Canvas Backpack', 'canvas-backpack', 'Durable canvas backpack with laptop compartment.', 'active', 'Bags', 'Becho', NOW(3)),
(1, 'Sunglasses', 'sunglasses', 'Polarized UV400 sunglasses.', 'active', 'Accessories', 'Becho', NOW(3)),
(1, 'Desk Lamp', 'desk-lamp', 'Modern LED desk lamp with adjustable brightness.', 'active', 'Home', 'Becho', NOW(3)),
(1, 'Wool Scarf', 'wool-scarf', 'Soft merino wool scarf.', 'active', 'Apparel', 'Becho', NOW(3)),
(1, 'Water Bottle', 'water-bottle', 'Insulated stainless steel water bottle, 750ml.', 'active', 'Accessories', 'Becho', NOW(3)),
(1, 'Throw Pillow', 'throw-pillow', 'Decorative throw pillow with premium filling.', 'active', 'Home', 'Becho', NOW(3));

-- Variants: one per product (price in minor units = BDT * 100).
-- Some have compareAt discounts.
INSERT IGNORE INTO product_variants (product_id, store_id, sku, option1, price_minor, compare_at_minor)
SELECT p.id, 1, CONCAT('DEMO-', p.id), 'Default',
  CASE p.slug
    WHEN 'classic-sneakers' THEN 7990
    WHEN 'leather-wallet' THEN 4590
    WHEN 'everyday-tote' THEN 2990
    WHEN 'denim-jacket' THEN 12990
    WHEN 'ceramic-mug' THEN 1890
    WHEN 'cotton-tee' THEN 2590
    WHEN 'canvas-backpack' THEN 5990
    WHEN 'sunglasses' THEN 3490
    WHEN 'desk-lamp' THEN 4990
    WHEN 'wool-scarf' THEN 3290
    WHEN 'water-bottle' THEN 2290
    WHEN 'throw-pillow' THEN 2790
    ELSE 9990
  END,
  CASE p.slug
    WHEN 'classic-sneakers' THEN 9990
    WHEN 'denim-jacket' THEN 15990
    WHEN 'canvas-backpack' THEN 7990
    ELSE NULL
  END
FROM products p WHERE p.store_id = 1 AND p.slug IN (
  'classic-sneakers','leather-wallet','everyday-tote','denim-jacket','ceramic-mug','cotton-tee',
  'canvas-backpack','sunglasses','desk-lamp','wool-scarf','water-bottle','throw-pillow');

-- Product images (Unsplash URLs).
INSERT IGNORE INTO product_images (product_id, url, alt, position)
SELECT p.id, img.url, p.title, 0
FROM products p
JOIN (
  SELECT 'classic-sneakers' AS slug, 'https://images.unsplash.com/photo-1549298916-b41d501d3772?w=600&q=80&auto=format&fit=crop' AS url UNION ALL
  SELECT 'leather-wallet', 'https://images.unsplash.com/photo-1627123424574-724758594e93?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'everyday-tote', 'https://images.unsplash.com/photo-1590874103328-eac38a683ce7?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'denim-jacket', 'https://images.unsplash.com/photo-1551537482-f2075a1d41f2?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'ceramic-mug', 'https://images.unsplash.com/photo-1514228742587-6b1558fcca3d?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'cotton-tee', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'canvas-backpack', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'sunglasses', 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'desk-lamp', 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'wool-scarf', 'https://images.unsplash.com/photo-1601924994987-69e26d50dc26?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'water-bottle', 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&q=80&auto=format&fit=crop' UNION ALL
  SELECT 'throw-pillow', 'https://images.unsplash.com/photo-1584100936595-c065efb6a3e3?w=600&q=80&auto=format&fit=crop'
) img ON img.slug = p.slug COLLATE utf8mb4_unicode_ci

WHERE p.store_id = 1;

-- Demo customers.
INSERT IGNORE INTO customers (store_id, email, phone, first_name, last_name, accepts_marketing, status)
VALUES
(1, 'rahim@example.com', '01700000001', 'Rahim', 'Uddin', 1, 'active'),
(1, 'nadia@example.com', '01700000002', 'Nadia', 'Islam', 1, 'active');
