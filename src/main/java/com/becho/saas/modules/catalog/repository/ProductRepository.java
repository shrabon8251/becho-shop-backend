package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

    /** Keyset page: newest first. */
    @Query("""
      SELECT p FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (:status IS NULL OR p.status = :status)
        AND (:cursorAt IS NULL OR p.createdAt < :cursorAt
             OR (p.createdAt = :cursorAt AND p.id < :cursorId))
      ORDER BY p.createdAt DESC, p.id DESC
      """)
    List<Product> findPage(Long storeId, String status,
                           LocalDateTime cursorAt, Long cursorId,
                           org.springframework.data.domain.Limit limit);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.store.id = :storeId AND p.deletedAt IS NULL")
    long countActive(Long storeId);

    /** All non-deleted products (the old countActive name counts everything — kept as-is). */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.store.id = :storeId AND p.deletedAt IS NULL")
    long countAll(Long storeId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.store.id = :storeId AND p.deletedAt IS NULL AND p.status = :status")
    long countByStatus(Long storeId, String status);

    /**
     * Low-stock products: total on-hand across all variants is 1..4.
     * Stock lives in inventory_levels (per variant, per location) — not on the variant row.
     * NOTE: COALESCE needs 0L (Long), not 0 — SUM returns Long and IntelliJ
     * flags "Type mismatch" for the mixed types (2026-11-11).
     */
    @Query("""
      SELECT COUNT(p) FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
             WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) BETWEEN 1 AND 4
      """)
    long countLowStock(Long storeId);

    /**
     * Admin Products page: server-side paging. Only one page (20 rows) is ever
     * loaded — the old list endpoint always pulled 100 rows at once.
     * The three methods share the same filters; they differ only in ordering:
     * newest/title sort via Pageable, price sorts need their own ORDER BY
     * (min variant price is not a Product attribute, so Pageable can't sort it).
     * Stock filter uses the same 1..4 low-stock rule as countLowStock.
     */
    @Query("""
      SELECT p FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (:status IS NULL OR p.status = :status)
        AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:collectionId IS NULL OR EXISTS (SELECT cp FROM CollectionProduct cp
             WHERE cp.product = p AND cp.collection.id = :collectionId))
        AND (:stock IS NULL OR CASE
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) = 0 THEN 'out'
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) BETWEEN 1 AND 4 THEN 'low'
             ELSE 'in' END = :stock)
      """)
    org.springframework.data.domain.Page<Product> findAdminPage(
            Long storeId, String status, String q, Long collectionId, String stock,
            org.springframework.data.domain.Pageable pageable);

    @Query(value = """
      SELECT p FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (:status IS NULL OR p.status = :status)
        AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:collectionId IS NULL OR EXISTS (SELECT cp FROM CollectionProduct cp
             WHERE cp.product = p AND cp.collection.id = :collectionId))
        AND (:stock IS NULL OR CASE
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) = 0 THEN 'out'
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) BETWEEN 1 AND 4 THEN 'low'
             ELSE 'in' END = :stock)
      ORDER BY (SELECT COALESCE(MIN(v.priceMinor), 0) FROM ProductVariant v
                WHERE v.product = p AND v.deletedAt IS NULL) ASC
      """,
            countQuery = """
      SELECT COUNT(p) FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (:status IS NULL OR p.status = :status)
        AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:collectionId IS NULL OR EXISTS (SELECT cp FROM CollectionProduct cp
             WHERE cp.product = p AND cp.collection.id = :collectionId))
        AND (:stock IS NULL OR CASE
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) = 0 THEN 'out'
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) BETWEEN 1 AND 4 THEN 'low'
             ELSE 'in' END = :stock)
      """)
    org.springframework.data.domain.Page<Product> findAdminPagePriceAsc(
            Long storeId, String status, String q, Long collectionId, String stock,
            org.springframework.data.domain.Pageable pageable);

    @Query(value = """
      SELECT p FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (:status IS NULL OR p.status = :status)
        AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:collectionId IS NULL OR EXISTS (SELECT cp FROM CollectionProduct cp
             WHERE cp.product = p AND cp.collection.id = :collectionId))
        AND (:stock IS NULL OR CASE
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) = 0 THEN 'out'
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) BETWEEN 1 AND 4 THEN 'low'
             ELSE 'in' END = :stock)
      ORDER BY (SELECT COALESCE(MIN(v.priceMinor), 0) FROM ProductVariant v
                WHERE v.product = p AND v.deletedAt IS NULL) DESC
      """,
            countQuery = """
      SELECT COUNT(p) FROM Product p
      WHERE p.store.id = :storeId AND p.deletedAt IS NULL
        AND (:status IS NULL OR p.status = :status)
        AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:collectionId IS NULL OR EXISTS (SELECT cp FROM CollectionProduct cp
             WHERE cp.product = p AND cp.collection.id = :collectionId))
        AND (:stock IS NULL OR CASE
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) = 0 THEN 'out'
             WHEN (SELECT COALESCE(SUM(il.onHand), 0L) FROM InventoryLevel il
                   WHERE il.variant.product = p AND il.variant.deletedAt IS NULL) BETWEEN 1 AND 4 THEN 'low'
             ELSE 'in' END = :stock)
      """)
    org.springframework.data.domain.Page<Product> findAdminPagePriceDesc(
            Long storeId, String status, String q, Long collectionId, String stock,
            org.springframework.data.domain.Pageable pageable);
}
