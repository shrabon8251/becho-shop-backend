package com.becho.saas.modules.customers.repository;

import com.becho.saas.modules.customers.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  Optional<Customer> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

  Optional<Customer> findByStoreIdAndEmailAndDeletedAtIsNull(Long storeId, String email);

  @Query("""
      SELECT c FROM Customer c
      WHERE c.store.id = :storeId AND c.deletedAt IS NULL
        AND (:cursorAt IS NULL OR c.createdAt < :cursorAt
             OR (c.createdAt = :cursorAt AND c.id < :cursorId))
      ORDER BY c.createdAt DESC, c.id DESC
      """)
  List<Customer> findPage(Long storeId, LocalDateTime cursorAt, Long cursorId,
                          org.springframework.data.domain.Limit limit);
}
