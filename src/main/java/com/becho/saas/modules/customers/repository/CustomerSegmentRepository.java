package com.becho.saas.modules.customers.repository;

import com.becho.saas.modules.customers.entity.CustomerSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, Long> {

  List<CustomerSegment> findByStoreIdOrderByCreatedAtDesc(Long storeId);

  Optional<CustomerSegment> findByIdAndStoreId(Long id, Long storeId);
}
