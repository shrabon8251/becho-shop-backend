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
}
