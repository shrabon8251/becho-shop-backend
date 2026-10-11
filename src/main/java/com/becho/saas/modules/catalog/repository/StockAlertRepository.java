package com.becho.saas.modules.catalog.repository;

import com.becho.saas.modules.catalog.entity.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

  List<StockAlert> findByStoreIdAndNotifiedFalse(Long storeId);

  List<StockAlert> findByStoreIdAndProductIdAndNotifiedFalse(Long storeId, Long productId);

  boolean existsByStoreIdAndProductIdAndEmail(Long storeId, Long productId, String email);

  StockAlert findByStoreIdAndProductIdAndEmail(Long storeId, Long productId, String email);
}
