package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.OrderTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderTransactionRepository extends JpaRepository<OrderTransaction, Long> {
  List<OrderTransaction> findByOrderIdOrderByCreatedAt(Long orderId);
  boolean existsByStoreIdAndGatewayAndGatewayTxnId(Long storeId, String gateway, String gatewayTxnId);
  List<OrderTransaction> findByOrderIdAndKindAndStatus(Long orderId, String kind, String status);
}
