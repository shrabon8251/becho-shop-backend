package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.DraftOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DraftOrderRepository extends JpaRepository<DraftOrder, Long> {

  List<DraftOrder> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<DraftOrder> findByIdAndStoreId(Long id, Long storeId);
}
