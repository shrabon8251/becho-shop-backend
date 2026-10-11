package com.becho.saas.modules.orders.repository;

import com.becho.saas.modules.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

  Optional<Order> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

  @Query("""
      SELECT o FROM Order o LEFT JOIN FETCH o.lines
      WHERE o.id = :id AND o.store.id = :storeId AND o.deletedAt IS NULL
      """)
  Optional<Order> findWithLines(Long id, Long storeId);

  @Query("""
      SELECT o FROM Order o
      WHERE o.store.id = :storeId AND o.deletedAt IS NULL
        AND (:financial IS NULL OR o.financialStatus = :financial)
        AND (:cursorAt IS NULL OR o.createdAt < :cursorAt
             OR (o.createdAt = :cursorAt AND o.id < :cursorId))
      ORDER BY o.createdAt DESC, o.id DESC
      """)
  List<Order> findPage(Long storeId, String financial,
                       LocalDateTime cursorAt, Long cursorId,
                       org.springframework.data.domain.Limit limit);
}
