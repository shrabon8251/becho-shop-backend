package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

  /**
   * PERF fix (N+1): batch-initialize variants for a page of products.
   * Same persistence context as findPage, so the returned instances are
   * identical — p.getVariants() needs no extra query afterwards.
   */
  @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.id IN :ids")
  List<Product> findAllWithVariants(@Param("ids") List<Long> ids);

  /**
   * PERF fix (N+1): batch-initialize images for a page of products.
   */
  @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id IN :ids")
  List<Product> findAllWithImages(@Param("ids") List<Long> ids);

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
}
