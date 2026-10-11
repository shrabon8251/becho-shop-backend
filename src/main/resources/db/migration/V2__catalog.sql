-- V2: Product catalog (products, variants, images, collections, metafields)

CREATE TABLE IF NOT EXISTS products (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id      BIGINT UNSIGNED NOT NULL,
  title         VARCHAR(255) NOT NULL,
  slug          VARCHAR(255) NOT NULL,
  description   MEDIUMTEXT   NULL,
  status        VARCHAR(32)  NOT NULL DEFAULT 'draft',
  product_type  VARCHAR(128) NULL,
  vendor        VARCHAR(128) NULL,
  tags          JSON         NULL,
  published_at  DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at    DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_products_slug (store_id, slug),
  KEY idx_products_browse (store_id, status, published_at),
  FULLTEXT KEY ft_products_title (title),
  CONSTRAINT fk_products_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_products_status CHECK (status IN ('draft','active','archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_variants (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_id       BIGINT UNSIGNED NOT NULL,
  store_id         BIGINT UNSIGNED NOT NULL COMMENT 'denormalized for tenant-scoped indexes',
  sku              VARCHAR(64)  NULL,
  barcode          VARCHAR(64)  NULL,
  option1          VARCHAR(128) NULL,
  option2          VARCHAR(128) NULL,
  option3          VARCHAR(128) NULL,
  price_minor      BIGINT       NOT NULL,
  compare_at_minor BIGINT       NULL,
  cost_minor       BIGINT       NULL,
  weight_grams     INT          NULL,
  taxable          TINYINT(1)   NOT NULL DEFAULT 1,
  inventory_policy VARCHAR(32)  NOT NULL DEFAULT 'deny' COMMENT 'deny|continue',
  position         INT          NOT NULL DEFAULT 0,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at       DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_variants_sku (store_id, sku),
  KEY idx_variants_product (product_id, position),
  CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_variants_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_variants_price CHECK (price_minor >= 0),
  CONSTRAINT chk_variants_policy CHECK (inventory_policy IN ('deny','continue'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_images (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_id  BIGINT UNSIGNED NOT NULL,
  variant_id  BIGINT UNSIGNED NULL,
  url         VARCHAR(1024) NOT NULL,
  alt         VARCHAR(255)  NULL,
  position    INT           NOT NULL DEFAULT 0,
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_images_product (product_id, position),
  CONSTRAINT fk_images_product FOREIGN KEY (product_id) REFERENCES products (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS collections (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id      BIGINT UNSIGNED NOT NULL,
  title         VARCHAR(255) NOT NULL,
  slug          VARCHAR(255) NOT NULL,
  description   TEXT         NULL,
  image_url     VARCHAR(1024) NULL,
  sort_order    VARCHAR(32)  NOT NULL DEFAULT 'manual',
  status        VARCHAR(32)  NOT NULL DEFAULT 'draft',
  published_at  DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at    DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_collections_slug (store_id, slug),
  KEY idx_collections_store (store_id, status),
  CONSTRAINT fk_collections_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS collection_products (
  collection_id BIGINT UNSIGNED NOT NULL,
  product_id    BIGINT UNSIGNED NOT NULL,
  position      INT NOT NULL DEFAULT 0,
  PRIMARY KEY (collection_id, product_id),
  KEY idx_cp_product (product_id),
  CONSTRAINT fk_cp_collection FOREIGN KEY (collection_id) REFERENCES collections (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_cp_product FOREIGN KEY (product_id) REFERENCES products (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Flexible custom fields on products/variants/customers/orders (namespace.key).
CREATE TABLE IF NOT EXISTS product_metafields (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id    BIGINT UNSIGNED NOT NULL,
  owner_type  VARCHAR(32)  NOT NULL COMMENT 'product|variant|customer|order',
  owner_id    BIGINT UNSIGNED NOT NULL,
  namespace   VARCHAR(64)  NOT NULL,
  `key`       VARCHAR(64)  NOT NULL,
  value       JSON         NULL,
  value_type  VARCHAR(32)  NOT NULL DEFAULT 'string',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_metafields (store_id, owner_type, owner_id, namespace, `key`),
  CONSTRAINT fk_metafields_store FOREIGN KEY (store_id) REFERENCES stores (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
