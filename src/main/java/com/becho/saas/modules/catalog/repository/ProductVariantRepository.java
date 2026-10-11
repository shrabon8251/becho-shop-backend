package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
  List<ProductVariant> findByProductIdAndDeletedAtIsNullOrderByPosition(Long productId);
  Optional<ProductVariant> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);
}
