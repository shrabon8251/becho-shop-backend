package com.becho.saas.modules.marketing.repository;

import com.becho.saas.modules.marketing.entity.AbandonedCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AbandonedCheckoutRepository extends JpaRepository<AbandonedCheckout, Long> {
  List<AbandonedCheckout> findByStoreIdOrderByCreatedAtDesc(Long storeId);
  Optional<AbandonedCheckout> findByIdAndStoreId(Long id, Long storeId);
}
