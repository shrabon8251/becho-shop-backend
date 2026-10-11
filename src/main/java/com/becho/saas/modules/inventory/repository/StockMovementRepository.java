package com.becho.saas.modules.inventory.repository;

import com.becho.saas.modules.inventory.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
  List<StockMovement> findByStoreIdAndVariantIdOrderByCreatedAtDesc(Long storeId, Long variantId);
}
