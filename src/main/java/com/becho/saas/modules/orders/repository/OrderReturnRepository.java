package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

  List<OrderReturn> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<OrderReturn> findByIdAndStoreId(Long id, Long storeId);
}
